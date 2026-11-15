import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  FolderKanban, Plus, Heart, Bookmark, ExternalLink, Upload, Loader2, X, Code2, BookOpen
} from 'lucide-react';
import { useVybe } from '../../context/VybeContext';
import { VybeAvatar } from '../../components/OptimizedImage';
import { VybeEmptyState } from '../../components/VybeDesignSystem';
import { GrowProject } from '../../types';
import { createProject, fetchProjects, toggleProjectInteraction } from '../../services/growService';
import { uploadMediaBlobToStorage } from '../../services/supabaseService';

interface GrowProjectsTabProps {
  onOpenPrograms: () => void;
}

export const GrowProjectsTab: React.FC<GrowProjectsTabProps> = ({ onOpenPrograms }) => {
  const { currentUser, requireAuth, openUserProfile } = useVybe();

  const [projects, setProjects] = useState<GrowProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [stageFilter, setStageFilter] = useState<'ALL' | 'IDEA' | 'PROTOTYPE' | 'BUILDING' | 'LAUNCHED'>('ALL');
  const [collabOnly, setCollabOnly] = useState(false);

  // Create modal
  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [desc, setDesc] = useState('');
  const [stage, setStage] = useState<'IDEA' | 'PROTOTYPE' | 'BUILDING' | 'LAUNCHED'>('BUILDING');
  const [techStack, setTechStack] = useState('React, Kotlin, Supabase');
  const [needsRoles, setNeedsRoles] = useState('Designer, Backend');
  const [lookingForCollab, setLookingForCollab] = useState(true);
  const [repoUrl, setRepoUrl] = useState('');
  const [demoUrl, setDemoUrl] = useState('');
  const [mediaUrl, setMediaUrl] = useState('');
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mediaRef = useRef<HTMLInputElement | null>(null);

  const loadProjects = useCallback(async () => {
    setLoading(true);
    const list = await fetchProjects(currentUser?.id);
    setProjects(list);
    setLoading(false);
  }, [currentUser?.id]);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  const filtered = useMemo(() => {
    return projects.filter(p => {
      if (stageFilter !== 'ALL' && p.stage !== stageFilter) return false;
      if (collabOnly && !p.lookingForCollaborators) return false;
      return true;
    });
  }, [projects, stageFilter, collabOnly]);

  const handleUploadProjectMedia = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploadingMedia(true);
    const res = await uploadMediaBlobToStorage(file, 'projects', file.name, file.size);
    setUploadingMedia(false);
    if (res?.publicUrl) setMediaUrl(res.publicUrl);
  };

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!requireAuth('Sign in to showcase a project.') || !currentUser) return;
    if (!title.trim() || !summary.trim()) {
      setError('Project title and summary are required.');
      return;
    }
    setSaving(true);
    const res = await createProject({
      ownerId: currentUser.id,
      title,
      summary,
      description: desc || summary,
      category: 'Product',
      stage,
      techStack: techStack.split(',').map(s => s.trim()).filter(Boolean),
      mediaUrls: mediaUrl ? [mediaUrl] : [],
      repoUrl,
      demoUrl,
      lookingForCollaborators: lookingForCollab,
      needsRoles: lookingForCollab ? needsRoles.split(',').map(s => s.trim()).filter(Boolean) : []
    });
    setSaving(false);
    if (!res.ok) {
      setError(res.error || 'Could not publish project.');
      return;
    }
    setShowCreate(false);
    setTitle('');
    setSummary('');
    setDesc('');
    setMediaUrl('');
    await loadProjects();
  };

  const handleToggleLike = async (proj: GrowProject) => {
    if (!requireAuth('Sign in to appreciate projects.') || !currentUser) return;
    const nextLiked = !proj.isLiked;
    setProjects(prev =>
      prev.map(p =>
        p.id === proj.id
          ? { ...p, isLiked: nextLiked, likesCount: Math.max(0, p.likesCount + (nextLiked ? 1 : -1)) }
          : p
      )
    );
    await toggleProjectInteraction({
      projectId: proj.id,
      userId: currentUser.id,
      isLiked: nextLiked,
      isBookmarked: proj.isBookmarked
    });
  };

  const handleToggleBookmark = async (proj: GrowProject) => {
    if (!requireAuth('Sign in to bookmark projects.') || !currentUser) return;
    const nextSaved = !proj.isBookmarked;
    setProjects(prev =>
      prev.map(p =>
        p.id === proj.id
          ? { ...p, isBookmarked: nextSaved, bookmarksCount: Math.max(0, p.bookmarksCount + (nextSaved ? 1 : -1)) }
          : p
      )
    );
    await toggleProjectInteraction({
      projectId: proj.id,
      userId: currentUser.id,
      isLiked: proj.isLiked,
      isBookmarked: nextSaved
    });
  };

  return (
    <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">Projects Showcase</h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            Share builds, recruit collaborators & explore community prototypes
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onOpenPrograms}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs font-semibold"
          >
            <BookOpen className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
            <span>Programs</span>
          </button>
          <button
            type="button"
            onClick={() => {
              if (requireAuth('Sign in to publish a project.')) setShowCreate(true);
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold"
          >
            <Plus className="w-4 h-4" />
            <span>Publish Project</span>
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2">
        {(['ALL', 'IDEA', 'PROTOTYPE', 'BUILDING', 'LAUNCHED'] as const).map(st => (
          <button
            key={st}
            type="button"
            onClick={() => setStageFilter(st)}
            className={`px-3 py-1.5 rounded-full text-[11px] font-bold ${
              stageFilter === st
                ? 'bg-[var(--vybe-accent)] text-white keep-white'
                : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
            }`}
          >
            {st}
          </button>
        ))}
        <label className="ml-auto inline-flex items-center gap-1.5 text-xs cursor-pointer">
          <input
            type="checkbox"
            checked={collabOnly}
            onChange={e => setCollabOnly(e.target.checked)}
            className="accent-[var(--vybe-accent)]"
          />
          <span>Seeking Collaborators</span>
        </label>
      </div>

      {loading ? (
        <div className="py-12 flex items-center justify-center gap-2 text-xs text-[var(--vybe-text-secondary)]">
          <Loader2 className="w-4 h-4 animate-spin text-[var(--vybe-accent)]" /> Loading showcase...
        </div>
      ) : filtered.length === 0 ? (
        <VybeEmptyState
          icon={FolderKanban}
          title="No projects in this filter"
          subtitle="Be the first builder to share a prototype, open-source repo, or product build."
          actionLabel="Publish Project"
          onAction={() => {
            if (requireAuth('Sign in to publish a project.')) setShowCreate(true);
          }}
        />
      ) : (
        <div className="space-y-3.5">
          {filtered.map(proj => (
            <article key={proj.id} className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] overflow-hidden">
              {proj.mediaUrls[0] && (
                <img src={proj.mediaUrls[0]} alt={proj.title} className="w-full h-44 object-cover border-b border-[var(--vybe-border)]" />
              )}
              <div className="p-4 space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0 cursor-pointer" onClick={() => openUserProfile(proj.owner)}>
                    <VybeAvatar src={proj.owner.avatarUrl} name={proj.owner.displayName} size={30} className="w-7 h-7 shrink-0" />
                    <span className="text-xs font-bold truncate hover:underline">{proj.owner.displayName}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="px-2 py-0.5 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[10px] font-bold">
                      {proj.stage}
                    </span>
                    {proj.lookingForCollaborators && (
                      <span className="px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-500 text-[10px] font-bold">
                        Open Collab
                      </span>
                    )}
                  </div>
                </div>

                <div>
                  <h3 className="text-base font-bold text-[var(--vybe-text)]">{proj.title}</h3>
                  <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5 leading-relaxed">{proj.summary}</p>
                </div>

                {proj.techStack.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {proj.techStack.map(t => (
                      <span key={t} className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[10px] font-medium">
                        <Code2 className="w-3 h-3 text-[var(--vybe-accent)]" /> {t}
                      </span>
                    ))}
                  </div>
                )}

                {proj.lookingForCollaborators && proj.needsRoles.length > 0 && (
                  <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                    <strong className="text-[var(--vybe-text)]">Looking for:</strong> {proj.needsRoles.join(', ')}
                  </p>
                )}

                <div className="flex items-center justify-between pt-2 border-t border-[var(--vybe-border)]">
                  <div className="flex items-center gap-4">
                    <button
                      type="button"
                      onClick={() => handleToggleLike(proj)}
                      className={`inline-flex items-center gap-1 text-xs font-bold ${proj.isLiked ? 'text-rose-500' : 'text-[var(--vybe-text-secondary)]'}`}
                    >
                      <Heart className={`w-4 h-4 ${proj.isLiked ? 'fill-current' : ''}`} />
                      <span>{proj.likesCount}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => handleToggleBookmark(proj)}
                      className={`inline-flex items-center gap-1 text-xs font-bold ${proj.isBookmarked ? 'text-[var(--vybe-accent)]' : 'text-[var(--vybe-text-secondary)]'}`}
                    >
                      <Bookmark className={`w-4 h-4 ${proj.isBookmarked ? 'fill-current' : ''}`} />
                      <span>{proj.bookmarksCount}</span>
                    </button>
                  </div>
                  <div className="flex items-center gap-2">
                    {proj.demoUrl && (
                      <a href={proj.demoUrl} target="_blank" rel="noopener noreferrer" className="text-xs font-bold text-[var(--vybe-accent)] inline-flex items-center gap-1 hover:underline">
                        Demo <ExternalLink className="w-3 h-3" />
                      </a>
                    )}
                    {proj.repoUrl && (
                      <a href={proj.repoUrl} target="_blank" rel="noopener noreferrer" className="text-xs font-semibold text-[var(--vybe-text-secondary)] inline-flex items-center gap-1 hover:underline">
                        Repo <ExternalLink className="w-3 h-3" />
                      </a>
                    )}
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setShowCreate(false)}>
          <form onSubmit={handleCreateProject} onClick={e => e.stopPropagation()} className="w-full max-w-md max-h-[90dvh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-6 space-y-3 no-scrollbar">
            <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
              <h2 className="text-base font-bold">Publish Builder Project</h2>
              <button type="button" onClick={() => setShowCreate(false)} aria-label="Close"><X className="w-4 h-4" /></button>
            </div>
            {error && <p className="text-xs text-red-500">{error}</p>}
            <input type="text" required value={title} onChange={e => setTitle(e.target.value)} placeholder="Project Title" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="text" required value={summary} onChange={e => setSummary(e.target.value)} placeholder="One-line elevator pitch" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <textarea rows={2} value={desc} onChange={e => setDesc(e.target.value)} placeholder="Detailed problem & build overview..." className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="grid grid-cols-2 gap-2">
              <select value={stage} onChange={e => setStage(e.target.value as any)} className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs">
                <option value="IDEA">Idea</option>
                <option value="PROTOTYPE">Prototype</option>
                <option value="BUILDING">Building</option>
                <option value="LAUNCHED">Launched</option>
              </select>
              <div>
                <input ref={mediaRef} type="file" accept="image/*" onChange={handleUploadProjectMedia} className="hidden" />
                <button type="button" onClick={() => mediaRef.current?.click()} disabled={uploadingMedia} className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-semibold inline-flex items-center justify-center gap-1">
                  <Upload className="w-3.5 h-3.5" /> {uploadingMedia ? 'Uploading...' : mediaUrl ? 'Screenshot Ready' : 'Upload Image'}
                </button>
              </div>
            </div>
            <input type="text" value={techStack} onChange={e => setTechStack(e.target.value)} placeholder="Tech stack (comma separated)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <div className="grid grid-cols-2 gap-2">
              <input type="url" value={demoUrl} onChange={e => setDemoUrl(e.target.value)} placeholder="Demo URL (optional)" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
              <input type="url" value={repoUrl} onChange={e => setRepoUrl(e.target.value)} placeholder="Repo URL (optional)" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            </div>
            <label className="flex items-center gap-2 text-xs">
              <input type="checkbox" checked={lookingForCollab} onChange={e => setLookingForCollab(e.target.checked)} className="accent-[var(--vybe-accent)]" />
              <span>Looking for collaborators</span>
            </label>
            {lookingForCollab && (
              <input type="text" value={needsRoles} onChange={e => setNeedsRoles(e.target.value)} placeholder="Roles needed (e.g. Designer, ML Engineer)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            )}
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">Cancel</button>
              <button type="submit" disabled={saving} className="px-4 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-50">
                {saving ? 'Publishing...' : 'Publish'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
