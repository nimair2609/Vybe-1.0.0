import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  BookOpen, Plus, CheckCircle2, Circle, Loader2, Upload, ArrowLeft, Users, X
} from 'lucide-react';
import { useVybe } from '../../context/VybeContext';
import { VybeEmptyState } from '../../components/VybeDesignSystem';
import { GrowthProgram, GrowthProgramTask } from '../../types';
import {
  completeProgramTask,
  createGrowthProgram,
  fetchGrowthPrograms,
  fetchProgramTasks,
  joinOrLeaveProgram
} from '../../services/growService';
import { uploadMediaBlobToStorage } from '../../services/supabaseService';

interface GrowProgramsTabProps {
  onBack?: () => void;
}

export const GrowProgramsTab: React.FC<GrowProgramsTabProps> = ({ onBack }) => {
  const { currentUser, requireAuth } = useVybe();
  const [programs, setPrograms] = useState<GrowthProgram[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedProgram, setSelectedProgram] = useState<GrowthProgram | null>(null);
  const [tasks, setTasks] = useState<GrowthProgramTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);

  // Create program modal
  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [subtitle, setSubtitle] = useState('');
  const [desc, setDesc] = useState('');
  const [category, setCategory] = useState('Skill Building');
  const [durationDays, setDurationDays] = useState(14);
  const [taskLines, setTaskLines] = useState('Day 1: Define clear milestone\nDay 2: Ship first prototype');
  const [coverUrl, setCoverUrl] = useState('');
  const [uploadingCover, setUploadingCover] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const coverRef = useRef<HTMLInputElement | null>(null);

  const loadPrograms = useCallback(async () => {
    setLoading(true);
    const list = await fetchGrowthPrograms(currentUser?.id);
    setPrograms(list);
    setLoading(false);
  }, [currentUser?.id]);

  useEffect(() => {
    loadPrograms();
  }, [loadPrograms]);

  const openProgramTasks = async (program: GrowthProgram) => {
    setSelectedProgram(program);
    setTasksLoading(true);
    const list = await fetchProgramTasks(program.id, currentUser?.id);
    setTasks(list);
    setTasksLoading(false);
  };

  const handleUploadCover = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploadingCover(true);
    const res = await uploadMediaBlobToStorage(file, 'program-covers', file.name, file.size);
    setUploadingCover(false);
    if (res?.publicUrl) setCoverUrl(res.publicUrl);
  };

  const handleCreateProgram = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!requireAuth('Sign in to publish a Growth Program.') || !currentUser) return;
    if (!title.trim() || !desc.trim()) {
      setError('Program title and description are required.');
      return;
    }
    setCreating(true);
    const initialTasks = taskLines
      .split('\n')
      .map(s => s.trim())
      .filter(Boolean)
      .map(line => ({ title: line, instructions: '' }));

    const res = await createGrowthProgram({
      ownerId: currentUser.id,
      title,
      subtitle,
      description: desc,
      category,
      durationDays,
      coverUrl: coverUrl || undefined,
      initialTasks
    });
    setCreating(false);
    if (!res.ok) {
      setError(res.error || 'Could not create program.');
      return;
    }
    setShowCreate(false);
    setTitle('');
    setSubtitle('');
    setDesc('');
    setCoverUrl('');
    await loadPrograms();
  };

  const handleJoinToggle = async (prog: GrowthProgram) => {
    if (!requireAuth('Sign in to enroll in programs.') || !currentUser) return;
    const ok = await joinOrLeaveProgram(prog.id, currentUser.id, !prog.isJoined);
    if (ok) await loadPrograms();
  };

  const handleCompleteTask = async (task: GrowthProgramTask) => {
    if (!requireAuth('Sign in to log program progress.') || !currentUser) return;
    const ok = await completeProgramTask(task.id, currentUser.id);
    if (ok && selectedProgram) {
      await openProgramTasks(selectedProgram);
    }
  };

  return (
    <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              aria-label="Back to Grow Home"
              className="p-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)]"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
          )}
          <div>
            <h1 className="text-xl font-extrabold text-[var(--vybe-text)]">Growth Programs</h1>
            <p className="text-xs text-[var(--vybe-text-secondary)]">Structured challenges & guided tracks</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => {
            if (requireAuth('Sign in to launch a Growth Program.')) setShowCreate(true);
          }}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span>New Program</span>
        </button>
      </div>

      {loading ? (
        <div className="py-12 flex items-center justify-center gap-2 text-xs text-[var(--vybe-text-secondary)]">
          <Loader2 className="w-4 h-4 animate-spin text-[var(--vybe-accent)]" />
          <span>Loading programs...</span>
        </div>
      ) : programs.length === 0 ? (
        <VybeEmptyState
          icon={BookOpen}
          title="No Growth Programs yet"
          subtitle="Launch the first structured 7-day or 14-day challenge for the VYBE community."
          actionLabel="Create Program"
          onAction={() => setShowCreate(true)}
        />
      ) : (
        <div className="space-y-3">
          {programs.map(p => (
            <div
              key={p.id}
              onClick={() => openProgramTasks(p)}
              className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] overflow-hidden cursor-pointer transition-all"
            >
              {p.coverUrl && (
                <img src={p.coverUrl} alt={p.title} className="w-full h-32 object-cover border-b border-[var(--vybe-border)]" />
              )}
              <div className="p-4 flex items-start justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="px-2 py-0.5 rounded bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[10px] font-bold">
                      {p.category}
                    </span>
                    <span className="text-[11px] text-[var(--vybe-text-muted)]">
                      {p.durationDays} days · <Users className="w-3 h-3 inline" /> {p.participantsCount}
                    </span>
                  </div>
                  <h3 className="text-sm font-bold text-[var(--vybe-text)]">{p.title}</h3>
                  {p.subtitle && <p className="text-xs font-medium text-[var(--vybe-text-secondary)]">{p.subtitle}</p>}
                  <p className="text-xs text-[var(--vybe-text-secondary)] line-clamp-2">{p.description}</p>
                </div>
                <button
                  type="button"
                  onClick={e => {
                    e.stopPropagation();
                    handleJoinToggle(p);
                  }}
                  className={`px-3.5 py-2 rounded-xl text-xs font-bold shrink-0 ${
                    p.isJoined
                      ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30'
                      : 'bg-[var(--vybe-accent)] text-white keep-white'
                  }`}
                >
                  {p.isJoined ? 'Enrolled' : 'Join Track'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Program Tasks Drawer Modal */}
      {selectedProgram && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setSelectedProgram(null)}>
          <div onClick={e => e.stopPropagation()} className="w-full max-w-md max-h-[85dvh] flex flex-col bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-5 space-y-3">
            <div className="flex items-start justify-between gap-2 pb-2 border-b border-[var(--vybe-border)]">
              <div>
                <h3 className="text-sm font-extrabold">{selectedProgram.title}</h3>
                <p className="text-[11px] text-[var(--vybe-text-secondary)]">{selectedProgram.durationDays}-day curriculum</p>
              </div>
              <button type="button" onClick={() => setSelectedProgram(null)} aria-label="Close" className="p-1 text-[var(--vybe-text-secondary)]">
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto space-y-2 no-scrollbar">
              {tasksLoading ? (
                <div className="py-8 flex items-center justify-center gap-2 text-xs text-[var(--vybe-text-secondary)]">
                  <Loader2 className="w-4 h-4 animate-spin" /> Loading tasks...
                </div>
              ) : tasks.length === 0 ? (
                <p className="text-xs text-[var(--vybe-text-secondary)] text-center py-6">No tasks listed for this program yet.</p>
              ) : (
                tasks.map(t => (
                  <div
                    key={t.id}
                    onClick={() => handleCompleteTask(t)}
                    className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] flex items-start gap-2.5 cursor-pointer"
                  >
                    {t.isCompletedToday ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                    ) : (
                      <Circle className="w-4 h-4 text-[var(--vybe-text-muted)] shrink-0 mt-0.5" />
                    )}
                    <div className="min-w-0">
                      <span className="text-[10px] font-bold text-[var(--vybe-accent)]">DAY {t.dayNumber}</span>
                      <p className="text-xs font-bold">{t.title}</p>
                      {t.instructions && <p className="text-[11px] text-[var(--vybe-text-secondary)] mt-0.5">{t.instructions}</p>}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* Create Program Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setShowCreate(false)}>
          <form
            onSubmit={handleCreateProgram}
            onClick={e => e.stopPropagation()}
            className="w-full max-w-md max-h-[90dvh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-6 space-y-3 no-scrollbar"
          >
            <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
              <h2 className="text-base font-bold">Launch Growth Program</h2>
              <button type="button" onClick={() => setShowCreate(false)} aria-label="Close" className="p-1">
                <X className="w-4 h-4" />
              </button>
            </div>
            {error && <p className="text-xs text-red-500">{error}</p>}
            <input type="text" required value={title} onChange={e => setTitle(e.target.value)} placeholder="Program title" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="text" value={subtitle} onChange={e => setSubtitle(e.target.value)} placeholder="Subtitle (optional)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <textarea rows={2} required value={desc} onChange={e => setDesc(e.target.value)} placeholder="Describe what participants will accomplish..." className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="grid grid-cols-2 gap-2">
              <input type="text" value={category} onChange={e => setCategory(e.target.value)} placeholder="Category" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
              <input type="number" min={1} max={365} value={durationDays} onChange={e => setDurationDays(Number(e.target.value))} placeholder="Days" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            </div>
            <div className="flex items-center gap-2">
              <input ref={coverRef} type="file" accept="image/*" onChange={handleUploadCover} className="hidden" />
              <button type="button" onClick={() => coverRef.current?.click()} disabled={uploadingCover} className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-semibold inline-flex items-center gap-1.5">
                <Upload className="w-3.5 h-3.5" /> {uploadingCover ? 'Uploading...' : coverUrl ? 'Cover Uploaded' : 'Upload Cover Image'}
              </button>
            </div>
            <textarea rows={3} value={taskLines} onChange={e => setTaskLines(e.target.value)} placeholder="Daily tasks (one per line)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">Cancel</button>
              <button type="submit" disabled={creating} className="px-4 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-50">
                {creating ? 'Publishing...' : 'Publish Program'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
