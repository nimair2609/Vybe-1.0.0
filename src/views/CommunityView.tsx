import React, { useState, useMemo } from 'react';
import { Users, Plus, Check, Search, Clock, Lock, X, Loader2 } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { VybeEmptyState, VybeConfirmDialog } from '../components/VybeDesignSystem';
import { VybeAvatar } from '../components/OptimizedImage';
import { CircleJoinMode, Community } from '../types';
import { createCircleInSupabase } from '../services/storiesCirclesService';
import { CircleWorkspaceModal } from './CircleWorkspaceModal';

export const CommunityView: React.FC = () => {
  const { communities, toggleJoinCommunity, currentUser, requireAuth, refreshCommunities } = useVybe();
  const [search, setSearch] = useState('');
  const [activeCircle, setActiveCircle] = useState<Community | null>(null);
  const [leaveTarget, setLeaveTarget] = useState<Community | null>(null);

  // Create Circle Modal State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newCategory, setNewCategory] = useState('Growth');
  const [newJoinMode, setNewJoinMode] = useState<CircleJoinMode>('PUBLIC');
  const [newRules, setNewRules] = useState('');
  const [newTags, setNewTags] = useState('builders, growth');
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return communities;
    return communities.filter(
      c =>
        c.name.toLowerCase().includes(q) ||
        c.category.toLowerCase().includes(q) ||
        c.description.toLowerCase().includes(q)
    );
  }, [communities, search]);

  const syncedActiveCircle = useMemo(
    () => (activeCircle ? communities.find(c => c.id === activeCircle.id) || activeCircle : null),
    [activeCircle, communities]
  );

  const handleCreateCircle = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);
    if (!requireAuth('Sign in to create a VYBE Circle.') || !currentUser) return;
    if (!newName.trim() || !newDesc.trim()) {
      setCreateError('Please enter a Circle name and description.');
      return;
    }

    setIsCreating(true);
    const res = await createCircleInSupabase({
      creatorId: currentUser.id,
      name: newName,
      description: newDesc,
      category: newCategory,
      joinMode: newJoinMode,
      rules: newRules,
      tags: newTags.split(',').map(s => s.trim().replace(/^#/, '')).filter(Boolean)
    });
    setIsCreating(false);

    if (!res.ok || !res.circle) {
      setCreateError(res.error || 'Could not create Circle.');
      return;
    }

    setNewName('');
    setNewDesc('');
    setNewRules('');
    setShowCreateModal(false);
    await refreshCommunities();
    setActiveCircle(res.circle);
  };

  const handleMembershipAction = (e: React.MouseEvent, comm: Community) => {
    e.stopPropagation();
    if (comm.isJoined) {
      setLeaveTarget(comm);
    } else {
      toggleJoinCommunity(comm.id);
    }
  };

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-6 pb-28 lg:pb-12 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border border-[var(--vybe-border)]">
            <Users className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-extrabold brand-font text-[var(--vybe-text)]">
              VYBE Growth Circles
            </h1>
            <p className="text-xs text-[var(--vybe-text-secondary)]">
              Collaborative spaces with polls, events, resources, roles & group chats.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <div className="relative flex-1 sm:w-56">
            <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter circles..."
              className="w-full pl-9 pr-3.5 py-2 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
            />
          </div>
          <button
            type="button"
            onClick={() => {
              if (requireAuth('Sign in to create a Circle.')) setShowCreateModal(true);
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shrink-0 hover:opacity-95"
          >
            <Plus className="w-4 h-4" />
            <span>Create Circle</span>
          </button>
        </div>
      </div>

      {/* Communities Grid */}
      {filtered.length === 0 ? (
        <VybeEmptyState
          icon={Users}
          title="No matching Circles"
          subtitle="Start a new Growth Circle or refine your search filter."
          actionLabel={search ? 'Clear Filter' : 'Create Circle'}
          onAction={search ? () => setSearch('') : () => setShowCreateModal(true)}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filtered.map(comm => (
            <div
              key={comm.id}
              onClick={() => setActiveCircle(comm)}
              className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] overflow-hidden hover:border-[var(--vybe-accent)]/60 transition-all flex flex-col cursor-pointer"
            >
              {/* Banner */}
              <div className="h-28 w-full relative">
                {comm.bannerUrl ? (
                  <img src={comm.bannerUrl} alt={comm.name} className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full bg-gradient-to-br from-[#1e1b4b] via-[#312e81] to-[#0c4a6e]" />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-[var(--vybe-surface)] via-transparent to-transparent" />
                {comm.joinMode && comm.joinMode !== 'PUBLIC' && (
                  <span className="absolute top-2.5 right-2.5 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-black/60 text-white keep-white text-[10px] font-bold">
                    <Lock className="w-2.5 h-2.5" /> {comm.joinMode}
                  </span>
                )}
              </div>

              {/* Info */}
              <div className="p-4 pt-0 -mt-6 relative flex-1 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <VybeAvatar
                      src={comm.avatarUrl}
                      name={comm.name}
                      size={56}
                      className="w-14 h-14 rounded-2xl border-2 border-[var(--vybe-surface)] shadow-md"
                    />
                    <button
                      type="button"
                      onClick={(e) => handleMembershipAction(e, comm)}
                      className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all ${
                        comm.isJoined
                          ? 'bg-[var(--vybe-elevated)] text-[var(--vybe-text)] border border-[var(--vybe-border)] hover:border-red-500/60 hover:text-red-500'
                          : comm.isPending
                            ? 'bg-amber-500/15 border border-amber-500/40 text-amber-500'
                            : 'bg-[var(--vybe-accent)] text-white keep-white hover:opacity-95'
                      }`}
                    >
                      {comm.isJoined ? (
                        <>
                          <Check className="w-3.5 h-3.5" />
                          <span>Joined</span>
                        </>
                      ) : comm.isPending ? (
                        <>
                          <Clock className="w-3.5 h-3.5" />
                          <span>Pending</span>
                        </>
                      ) : (
                        <>
                          <Plus className="w-3.5 h-3.5" />
                          <span>{comm.joinMode === 'APPROVAL' || comm.joinMode === 'PRIVATE' ? 'Request Join' : 'Join Circle'}</span>
                        </>
                      )}
                    </button>
                  </div>

                  <div className="mt-2.5">
                    <h3 className="font-bold text-[var(--vybe-text)] text-base">{comm.name}</h3>
                    <span className="text-[10px] text-[var(--vybe-accent)] font-semibold uppercase tracking-wider">
                      {comm.category} · {comm.membersCount.toLocaleString()} members
                    </span>
                    <p className="text-xs text-[var(--vybe-text-secondary)] mt-1.5 leading-relaxed line-clamp-2">
                      {comm.description}
                    </p>
                  </div>
                </div>

                {/* Tags */}
                <div className="flex flex-wrap gap-1.5 mt-4 pt-3 border-t border-[var(--vybe-border)]">
                  {comm.tags.map(t => (
                    <span
                      key={t}
                      className="text-[10px] px-2 py-0.5 rounded-md bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] border border-[var(--vybe-border)]"
                    >
                      #{t}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Circle Workspace Modal */}
      {syncedActiveCircle && (
        <CircleWorkspaceModal
          circle={syncedActiveCircle}
          onClose={() => setActiveCircle(null)}
        />
      )}

      {/* Mandatory Leave Circle Confirmation Dialog */}
      <VybeConfirmDialog
        open={Boolean(leaveTarget)}
        title={`Leave ${leaveTarget?.name || 'Circle'}?`}
        description="Leaving removes you from this Circle's workspace, events, and group chat."
        confirmLabel="Leave Circle"
        tone="danger"
        onConfirm={async () => {
          const target = leaveTarget;
          setLeaveTarget(null);
          if (target) await toggleJoinCommunity(target.id);
        }}
        onCancel={() => setLeaveTarget(null)}
      />

      {/* Create Circle Modal */}
      {showCreateModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm"
          onClick={() => setShowCreateModal(false)}
        >
          <form
            onSubmit={handleCreateCircle}
            onClick={e => e.stopPropagation()}
            className="w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-6 space-y-3.5 text-[var(--vybe-text)] shadow-2xl"
          >
            <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
              <h2 className="text-base font-bold">Create a Growth Circle</h2>
              <button type="button" onClick={() => setShowCreateModal(false)} aria-label="Close" className="p-1 text-[var(--vybe-text-secondary)]">
                <X className="w-4 h-4" />
              </button>
            </div>

            {createError && (
              <div className="p-2.5 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs">{createError}</div>
            )}

            <input
              type="text"
              required
              value={newName}
              onChange={e => setNewName(e.target.value)}
              placeholder="Circle Name (e.g. Indie AI Builders)"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
            />
            <textarea
              rows={2}
              required
              value={newDesc}
              onChange={e => setNewDesc(e.target.value)}
              placeholder="What will members build or learn together?"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
            />
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                value={newCategory}
                onChange={e => setNewCategory(e.target.value)}
                placeholder="Category"
                className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
              />
              <select
                value={newJoinMode}
                onChange={e => setNewJoinMode(e.target.value as CircleJoinMode)}
                className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
              >
                <option value="PUBLIC">Public (Instant Join)</option>
                <option value="APPROVAL">Approval Required</option>
                <option value="PRIVATE">Private (Invite / Request)</option>
              </select>
            </div>
            <input
              type="text"
              value={newRules}
              onChange={e => setNewRules(e.target.value)}
              placeholder="Community rules (optional)"
              className="w-full px-3.5 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
            />
            <input
              type="text"
              value={newTags}
              onChange={e => setNewTags(e.target.value)}
              placeholder="Tags (comma separated)"
              className="w-full px-3.5 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
            />

            <div className="flex justify-end gap-2 pt-2">
              <button type="button" onClick={() => setShowCreateModal(false)} className="px-4 py-2 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">
                Cancel
              </button>
              <button
                type="submit"
                disabled={isCreating}
                className="px-5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold inline-flex items-center gap-1.5 disabled:opacity-50"
              >
                {isCreating && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                <span>{isCreating ? 'Creating...' : 'Launch Circle'}</span>
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
