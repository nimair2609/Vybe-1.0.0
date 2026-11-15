import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  Briefcase, Plus, CheckCircle2, Flag, Upload, Loader2, X, ExternalLink, ShieldCheck
} from 'lucide-react';
import { useVybe } from '../../context/VybeContext';
import { VybeEmptyState } from '../../components/VybeDesignSystem';
import { Opportunity, OpportunityApplication, OpportunityCategory } from '../../types';
import {
  createOpportunity,
  fetchMyOpportunityApplications,
  fetchOpportunities,
  submitOpportunityApplication,
  submitOpportunityReport
} from '../../services/growService';
import { uploadMediaBlobToStorage } from '../../services/supabaseService';

const CATEGORIES: OpportunityCategory[] = [
  'JOB', 'INTERNSHIP', 'FREELANCE', 'HACKATHON', 'MENTORSHIP',
  'WORKSHOP', 'GRANT', 'COLLABORATION', 'PARTNERSHIP', 'COMPETITION'
];

export const GrowOpportunitiesTab: React.FC = () => {
  const { currentUser, requireAuth } = useVybe();

  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const [applications, setApplications] = useState<OpportunityApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [catFilter, setCatFilter] = useState<'ALL' | OpportunityCategory>('ALL');
  const [feedback, setFeedback] = useState<string | null>(null);

  // Create opportunity modal
  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [orgName, setOrgName] = useState('');
  const [category, setCategory] = useState<OpportunityCategory>('INTERNSHIP');
  const [workMode, setWorkMode] = useState<'REMOTE' | 'HYBRID' | 'ONSITE'>('REMOTE');
  const [location, setLocation] = useState('Remote');
  const [compText, setCompText] = useState('');
  const [description, setDescription] = useState('');
  const [requirements, setRequirements] = useState('');
  const [skills, setSkills] = useState('TypeScript, Kotlin');
  const [sourceUrl, setSourceUrl] = useState('');
  const [posting, setPosting] = useState(false);

  // Apply modal
  const [applyTarget, setApplyTarget] = useState<Opportunity | null>(null);
  const [applyNote, setApplyNote] = useState('');
  const [applyPortfolio, setApplyPortfolio] = useState('');
  const [resumeUrl, setResumeUrl] = useState('');
  const [uploadingResume, setUploadingResume] = useState(false);
  const [submittingApp, setSubmittingApp] = useState(false);
  const resumeRef = useRef<HTMLInputElement | null>(null);

  // Report modal
  const [reportTarget, setReportTarget] = useState<Opportunity | null>(null);
  const [reportReason, setReportReason] = useState('Misleading compensation or scam');

  const loadAll = useCallback(async () => {
    setLoading(true);
    const [opps, apps] = await Promise.all([
      fetchOpportunities(),
      currentUser?.id ? fetchMyOpportunityApplications(currentUser.id) : Promise.resolve([])
    ]);
    setOpportunities(opps);
    setApplications(apps);
    setLoading(false);
  }, [currentUser?.id]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const appliedMap = useMemo(() => {
    const map = new Map<string, OpportunityApplication>();
    applications.forEach(a => map.set(a.opportunityId, a));
    return map;
  }, [applications]);

  const filtered = useMemo(() => {
    if (catFilter === 'ALL') return opportunities;
    return opportunities.filter(o => o.category === catFilter);
  }, [opportunities, catFilter]);

  const handleUploadResume = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploadingResume(true);
    const res = await uploadMediaBlobToStorage(file, 'resumes', file.name, file.size, { isPrivate: true });
    setUploadingResume(false);
    if (res?.publicUrl) {
      setResumeUrl(res.publicUrl);
    } else {
      setFeedback('Could not upload resume PDF/file.');
    }
  };

  const handleCreateOpp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to post an opportunity.') || !currentUser) return;
    if (!title.trim() || !orgName.trim() || !description.trim()) return;
    setPosting(true);
    const res = await createOpportunity({
      postedBy: currentUser.id,
      title,
      organizationName: orgName,
      category,
      workMode,
      location,
      compensationText: compText,
      description,
      requirements,
      skillsRequired: skills.split(',').map(s => s.trim()).filter(Boolean),
      sourceUrl
    });
    setPosting(false);
    if (res.ok) {
      setShowCreate(false);
      setTitle('');
      setOrgName('');
      setDescription('');
      setFeedback('Opportunity published!');
      await loadAll();
    } else {
      setFeedback(res.error || 'Could not post opportunity.');
    }
  };

  const handleSubmitApplication = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to apply.') || !currentUser || !applyTarget) return;
    setSubmittingApp(true);
    const res = await submitOpportunityApplication({
      opportunityId: applyTarget.id,
      applicantId: currentUser.id,
      note: applyNote,
      portfolioUrl: applyPortfolio,
      resumeUrl
    });
    setSubmittingApp(false);
    if (res.ok) {
      setApplyTarget(null);
      setApplyNote('');
      setApplyPortfolio('');
      setResumeUrl('');
      setFeedback(`Application submitted to ${applyTarget.organizationName}!`);
      await loadAll();
    } else {
      setFeedback(res.error || 'Failed submitting application.');
    }
  };

  const handleReportOpp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to report listings.') || !currentUser || !reportTarget) return;
    const ok = await submitOpportunityReport({
      opportunityId: reportTarget.id,
      reporterId: currentUser.id,
      reason: reportReason
    });
    setReportTarget(null);
    setFeedback(ok ? 'Opportunity report queued for admin review.' : 'Could not send report.');
  };

  return (
    <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">Verified Opportunities</h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            Jobs, internships, hackathons, grants & private resume applications
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            if (requireAuth('Sign in to post an opportunity.')) setShowCreate(true);
          }}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span>Post Opportunity</span>
        </button>
      </div>

      {feedback && (
        <div className="p-2.5 rounded-xl bg-[var(--vybe-accent-soft)] border border-[var(--vybe-accent)]/30 text-xs flex items-center justify-between gap-2">
          <span>{feedback}</span>
          <button type="button" onClick={() => setFeedback(null)} className="font-bold">Dismiss</button>
        </div>
      )}

      {/* Category Filter Strip */}
      <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pb-1">
        <button
          type="button"
          onClick={() => setCatFilter('ALL')}
          className={`shrink-0 px-3 py-1.5 rounded-full text-[11px] font-bold ${
            catFilter === 'ALL' ? 'bg-[var(--vybe-accent)] text-white keep-white' : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
          }`}
        >
          ALL
        </button>
        {CATEGORIES.map(c => (
          <button
            key={c}
            type="button"
            onClick={() => setCatFilter(c)}
            className={`shrink-0 px-3 py-1.5 rounded-full text-[11px] font-bold ${
              catFilter === c ? 'bg-[var(--vybe-accent)] text-white keep-white' : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
            }`}
          >
            {c}
          </button>
        ))}
      </div>

      {/* My Submitted Applications Strip */}
      {applications.length > 0 && (
        <div className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] space-y-2">
          <h3 className="text-xs font-bold">My Applications ({applications.length})</h3>
          <div className="space-y-1.5">
            {applications.slice(0, 4).map(app => (
              <div key={app.id} className="flex items-center justify-between text-[11px] p-2 rounded-xl bg-[var(--vybe-elevated)]">
                <span className="font-semibold truncate">{app.opportunityTitle || 'Opportunity'} · {app.organizationName}</span>
                <div className="flex items-center gap-2 shrink-0">
                  {app.resumeUrl && (
                    <a href={app.resumeUrl} target="_blank" rel="noopener noreferrer" className="text-[var(--vybe-accent)] hover:underline inline-flex items-center gap-0.5">
                      Resume <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                  <span className="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-500 font-bold">{app.status}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {loading ? (
        <div className="py-12 flex items-center justify-center gap-2 text-xs text-[var(--vybe-text-secondary)]">
          <Loader2 className="w-4 h-4 animate-spin text-[var(--vybe-accent)]" /> Loading opportunities...
        </div>
      ) : filtered.length === 0 ? (
        <VybeEmptyState
          icon={Briefcase}
          title="No opportunities listed yet"
          subtitle="Hackathons, internships, grants, and freelance collaborations appear here."
          actionLabel="Post Opportunity"
          onAction={() => {
            if (requireAuth('Sign in to post an opportunity.')) setShowCreate(true);
          }}
        />
      ) : (
        <div className="space-y-3">
          {filtered.map(opp => {
            const myApp = appliedMap.get(opp.id);
            return (
              <div key={opp.id} className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 space-y-2.5">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="px-2 py-0.5 rounded bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[10px] font-bold">
                        {opp.category}
                      </span>
                      <span className="px-2 py-0.5 rounded bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[10px] font-semibold">
                        {opp.workMode} · {opp.location}
                      </span>
                      {opp.isVerifiedOrg && (
                        <span className="inline-flex items-center gap-0.5 text-[10px] font-bold text-emerald-500">
                          <ShieldCheck className="w-3.5 h-3.5" /> Verified Org
                        </span>
                      )}
                    </div>
                    <h3 className="text-sm font-bold mt-1">{opp.title}</h3>
                    <p className="text-xs font-semibold text-[var(--vybe-text-secondary)]">
                      {opp.organizationName}{opp.compensationText ? ` · ${opp.compensationText}` : ''}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setReportTarget(opp)}
                    aria-label="Report listing"
                    className="p-1.5 rounded-lg text-[var(--vybe-text-muted)] hover:text-amber-500"
                  >
                    <Flag className="w-3.5 h-3.5" />
                  </button>
                </div>

                <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed">{opp.description}</p>

                {opp.skillsRequired.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {opp.skillsRequired.map(s => (
                      <span key={s} className="px-2 py-0.5 rounded bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[10px]">
                        {s}
                      </span>
                    ))}
                  </div>
                )}

                <div className="flex items-center justify-between pt-2 border-t border-[var(--vybe-border)]">
                  {opp.sourceUrl ? (
                    <a href={opp.sourceUrl} target="_blank" rel="noopener noreferrer" className="text-xs font-semibold text-[var(--vybe-accent)] inline-flex items-center gap-1 hover:underline">
                      Details <ExternalLink className="w-3 h-3" />
                    </a>
                  ) : <span />}

                  {myApp ? (
                    <span className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-500/15 text-emerald-500 text-xs font-bold">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Applied ({myApp.status})
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        if (requireAuth('Sign in to apply for opportunities.')) setApplyTarget(opp);
                      }}
                      className="px-4 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold"
                    >
                      Apply Now
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Post Opportunity Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setShowCreate(false)}>
          <form onSubmit={handleCreateOpp} onClick={e => e.stopPropagation()} className="w-full max-w-md max-h-[90dvh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-6 space-y-3 no-scrollbar">
            <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
              <h2 className="text-base font-bold">Post Opportunity</h2>
              <button type="button" onClick={() => setShowCreate(false)} aria-label="Close"><X className="w-4 h-4" /></button>
            </div>
            <input type="text" required value={title} onChange={e => setTitle(e.target.value)} placeholder="Role / Opportunity Title" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="text" required value={orgName} onChange={e => setOrgName(e.target.value)} placeholder="Organization / Studio Name" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="grid grid-cols-2 gap-2">
              <select value={category} onChange={e => setCategory(e.target.value as OpportunityCategory)} className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs">
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
              <select value={workMode} onChange={e => setWorkMode(e.target.value as any)} className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs">
                <option value="REMOTE">Remote</option>
                <option value="HYBRID">Hybrid</option>
                <option value="ONSITE">On-site</option>
              </select>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <input type="text" value={location} onChange={e => setLocation(e.target.value)} placeholder="Location" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
              <input type="text" value={compText} onChange={e => setCompText(e.target.value)} placeholder="Compensation / Prize" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            </div>
            <textarea rows={3} required value={description} onChange={e => setDescription(e.target.value)} placeholder="Scope, responsibilities, and timeline..." className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="text" value={skills} onChange={e => setSkills(e.target.value)} placeholder="Skills required (comma separated)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="url" value={sourceUrl} onChange={e => setSourceUrl(e.target.value)} placeholder="External link (optional)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">Cancel</button>
              <button type="submit" disabled={posting} className="px-4 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-50">
                {posting ? 'Posting...' : 'Publish'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Apply Modal */}
      {applyTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setApplyTarget(null)}>
          <form onSubmit={handleSubmitApplication} onClick={e => e.stopPropagation()} className="w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-6 space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
              <h2 className="text-sm font-bold truncate">Apply: {applyTarget.title}</h2>
              <button type="button" onClick={() => setApplyTarget(null)} aria-label="Close"><X className="w-4 h-4" /></button>
            </div>
            <textarea rows={3} required value={applyNote} onChange={e => setApplyNote(e.target.value)} placeholder="Why are you a strong fit?" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="url" value={applyPortfolio} onChange={e => setApplyPortfolio(e.target.value)} placeholder="Portfolio / GitHub / LinkedIn URL (optional)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="flex items-center justify-between gap-2">
              <input ref={resumeRef} type="file" accept=".pdf,image/*" onChange={handleUploadResume} className="hidden" />
              <button type="button" onClick={() => resumeRef.current?.click()} disabled={uploadingResume} className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-semibold inline-flex items-center gap-1.5">
                <Upload className="w-3.5 h-3.5" />
                <span>{uploadingResume ? 'Uploading Private Resume...' : resumeUrl ? 'Private Resume Attached' : 'Attach Private Resume (PDF)'}</span>
              </button>
            </div>
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setApplyTarget(null)} className="px-4 py-2 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">Cancel</button>
              <button type="submit" disabled={submittingApp} className="px-4 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-50">
                {submittingApp ? 'Submitting...' : 'Submit Application'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Report Opportunity Modal */}
      {reportTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setReportTarget(null)}>
          <form onSubmit={handleReportOpp} onClick={e => e.stopPropagation()} className="w-full max-w-sm bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl p-5 space-y-3">
            <h3 className="text-sm font-bold">Report {reportTarget.title}</h3>
            <select value={reportReason} onChange={e => setReportReason(e.target.value)} className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs">
              <option>Misleading compensation or scam</option>
              <option>Fake organization or impersonation</option>
              <option>Spam or expired listing</option>
            </select>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setReportTarget(null)} className="px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">Cancel</button>
              <button type="submit" className="px-4 py-1.5 rounded-xl bg-red-600 text-white keep-white text-xs font-bold">Report</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
