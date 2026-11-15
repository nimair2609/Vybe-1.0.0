import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  BookOpen, Users, FolderKanban, Sparkles, ArrowRight, Flame, CheckCircle2,
  Circle, Plus, Target, Compass, Loader2
} from 'lucide-react';
import { useVybe } from '../../context/VybeContext';
import { VybeProgressRing, VybeActivityMiniBars } from '../../components/VybeDesignSystem';
import { GrowthProgram, PersonalGoal, PersonalHabit } from '../../types';
import {
  createPersonalHabit,
  fetchGrowthPrograms,
  fetchPersonalGoals,
  fetchPersonalHabits,
  toggleHabitLog,
  upsertPersonalGoal
} from '../../services/growService';

interface GrowHomeTabProps {
  onOpenPrograms: () => void;
}

export const GrowHomeTab: React.FC<GrowHomeTabProps> = ({ onOpenPrograms }) => {
  const { currentUser, requireAuth, setGrowTab, communities, posts } = useVybe();

  const [habits, setHabits] = useState<PersonalHabit[]>([]);
  const [goals, setGoals] = useState<PersonalGoal[]>([]);
  const [programs, setPrograms] = useState<GrowthProgram[]>([]);
  const [loading, setLoading] = useState(true);

  // Quick composers
  const [habitTitle, setHabitTitle] = useState('');
  const [habitFreq, setHabitFreq] = useState<'DAILY' | 'WEEKLY'>('DAILY');
  const [savingHabit, setSavingHabit] = useState(false);

  const [goalTitle, setGoalTitle] = useState('');
  const [goalTargetDate, setGoalTargetDate] = useState('');
  const [savingGoal, setSavingGoal] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    const [progs, hList, gList] = await Promise.all([
      fetchGrowthPrograms(currentUser?.id),
      currentUser?.id ? fetchPersonalHabits(currentUser.id) : Promise.resolve([]),
      currentUser?.id ? fetchPersonalGoals(currentUser.id) : Promise.resolve([])
    ]);
    setPrograms(progs);
    setHabits(hList);
    setGoals(gList);
    setLoading(false);
  }, [currentUser?.id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleAddHabit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to track personal habits.') || !currentUser) return;
    if (!habitTitle.trim()) return;
    setSavingHabit(true);
    const res = await createPersonalHabit({
      userId: currentUser.id,
      title: habitTitle,
      category: 'Growth',
      frequency: habitFreq
    });
    setSavingHabit(false);
    if (res.ok) {
      setHabitTitle('');
      await loadData();
    }
  };

  const handleToggleHabit = async (habit: PersonalHabit) => {
    if (!requireAuth('Sign in to log habit check-ins.') || !currentUser) return;
    const ok = await toggleHabitLog(habit, currentUser.id);
    if (ok) await loadData();
  };

  const handleAddGoal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to save personal goals.') || !currentUser) return;
    if (!goalTitle.trim()) return;
    setSavingGoal(true);
    const ok = await upsertPersonalGoal({
      userId: currentUser.id,
      title: goalTitle,
      description: 'Personal milestone in VYBE GROW',
      targetDate: goalTargetDate || undefined,
      progressPercent: 0,
      status: 'ACTIVE',
      milestones: []
    });
    setSavingGoal(false);
    if (ok) {
      setGoalTitle('');
      setGoalTargetDate('');
      await loadData();
    }
  };

  const handleStepGoal = async (goal: PersonalGoal, delta: number) => {
    if (!currentUser) return;
    const nextPct = Math.max(0, Math.min(100, goal.progressPercent + delta));
    const ok = await upsertPersonalGoal({
      id: goal.id,
      userId: currentUser.id,
      title: goal.title,
      description: goal.description,
      targetDate: goal.targetDate,
      progressPercent: nextPct,
      status: nextPct >= 100 ? 'COMPLETED' : 'ACTIVE',
      milestones: goal.milestones
    });
    if (ok) await loadData();
  };

  const joinedCirclesCount = communities.filter(c => c.isJoined).length;
  const joinedProgramsCount = programs.filter(p => p.isJoined).length;
  const completedHabitsToday = habits.filter(h => h.completedToday).length;
  const totalCheckpoints = habits.length + goals.length + Math.max(programs.length, 1);
  const doneCheckpoints = completedHabitsToday + goals.filter(g => g.status === 'COMPLETED').length + joinedProgramsCount;
  const progressPct = totalCheckpoints > 0 ? Math.min(100, Math.round((doneCheckpoints / totalCheckpoints) * 100)) : 0;

  const activityBars = useMemo(() => {
    const counts = posts.slice(0, 10).map(p => Math.min(1, Math.max(0.18, (p.likesCount + p.comments.length * 2) / 30)));
    while (counts.length < 10) counts.push(0.2);
    return counts;
  }, [posts]);

  return (
    <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-5">
      {/* Hero Banner */}
      <section
        className="rounded-3xl p-5 border border-[var(--vybe-border)] shadow-sm relative overflow-hidden"
        style={{ background: 'linear-gradient(135deg, var(--vybe-hero-start), var(--vybe-hero-end))' }}
      >
        <div className="relative z-10 max-w-sm">
          <h1 className="text-xl sm:text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">
            Welcome back, {(currentUser?.displayName || 'Explorer').split(' ')[0]}
          </h1>
          <p className="text-xs sm:text-sm text-[var(--vybe-text-secondary)] mt-1">
            Build daily consistency, launch projects, and connect with mentors.
          </p>
          <button
            type="button"
            onClick={onOpenPrograms}
            className="mt-4 inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#0F172A] text-white keep-white text-xs font-bold hover:opacity-90 transition-opacity"
          >
            <span>Explore Growth Programs</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </section>

      {/* Progress Ring Card */}
      <section className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center gap-4">
        <VybeProgressRing percent={progressPct} size={72} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-bold text-[var(--vybe-text)]">Daily Momentum</p>
            <span className="text-[11px] font-semibold text-[var(--vybe-accent)]">
              {joinedProgramsCount} Programs · {joinedCirclesCount} Circles
            </span>
          </div>
          <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            {completedHabitsToday}/{habits.length} habits checked today · {goals.length} active goals
          </p>
          <div className="mt-2">
            <VybeActivityMiniBars values={activityBars} />
          </div>
        </div>
      </section>

      {/* Quick Access Tiles */}
      <div className="grid grid-cols-4 gap-2.5">
        {[
          { id: 'PROGRAMS', label: 'Programs', icon: BookOpen, onClick: onOpenPrograms },
          { id: 'CIRCLE', label: 'Network', icon: Users, onClick: () => setGrowTab('CIRCLE') },
          { id: 'PROJECTS', label: 'Projects', icon: FolderKanban, onClick: () => setGrowTab('PROJECTS') },
          { id: 'OPPORTUNITIES', label: 'Opportunities', icon: Sparkles, onClick: () => setGrowTab('OPPORTUNITIES') }
        ].map(tile => {
          const Icon = tile.icon;
          return (
            <button
              key={tile.id}
              type="button"
              onClick={tile.onClick}
              className="flex flex-col items-center justify-center gap-2 py-3.5 px-2 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] transition-colors"
            >
              <div className="w-10 h-10 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] flex items-center justify-center">
                <Icon className="w-5 h-5" />
              </div>
              <span className="text-[11px] font-semibold text-[var(--vybe-text)] truncate max-w-full">
                {tile.label}
              </span>
            </button>
          );
        })}
      </div>

      {/* Personal Habits & Streaks */}
      <section className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold flex items-center gap-1.5">
            <Flame className="w-4 h-4 text-amber-500" />
            Daily Habits & Streaks
          </h2>
          {loading && <Loader2 className="w-3.5 h-3.5 animate-spin text-[var(--vybe-accent)]" />}
        </div>

        <form onSubmit={handleAddHabit} className="flex items-center gap-2">
          <input
            type="text"
            value={habitTitle}
            onChange={e => setHabitTitle(e.target.value)}
            placeholder="New habit (e.g. Read 20 mins, Ship code)..."
            className="flex-1 px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
          />
          <select
            value={habitFreq}
            onChange={e => setHabitFreq(e.target.value as any)}
            className="px-2.5 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
          >
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
          </select>
          <button
            type="submit"
            disabled={savingHabit || !habitTitle.trim()}
            aria-label="Add habit"
            className="p-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white disabled:opacity-40"
          >
            <Plus className="w-4 h-4" />
          </button>
        </form>

        {habits.length === 0 ? (
          <p className="text-xs text-[var(--vybe-text-secondary)] py-2">
            No personal habits tracked yet. Add one above to start your streak.
          </p>
        ) : (
          <div className="space-y-2">
            {habits.map(h => (
              <div
                key={h.id}
                onClick={() => handleToggleHabit(h)}
                className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] flex items-center justify-between gap-3 cursor-pointer"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  {h.completedToday ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                  ) : (
                    <Circle className="w-4 h-4 text-[var(--vybe-text-muted)] shrink-0" />
                  )}
                  <div className="min-w-0">
                    <p className={`text-xs font-bold truncate ${h.completedToday ? 'line-through opacity-75' : ''}`}>
                      {h.title}
                    </p>
                    <p className="text-[10px] text-[var(--vybe-text-muted)]">
                      {h.targetFrequency} · Best {h.bestStreak}d
                    </p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-500 text-[11px] font-bold shrink-0">
                  <Flame className="w-3 h-3" /> {h.currentStreak}d
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Personal Goals */}
      <section className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 space-y-3">
        <h2 className="text-sm font-bold flex items-center gap-1.5">
          <Target className="w-4 h-4 text-[var(--vybe-accent)]" />
          Personal Goals & Milestones
        </h2>

        <form onSubmit={handleAddGoal} className="flex flex-wrap sm:flex-nowrap items-center gap-2">
          <input
            type="text"
            value={goalTitle}
            onChange={e => setGoalTitle(e.target.value)}
            placeholder="Goal (e.g. Launch SaaS MVP)..."
            className="flex-1 min-w-[160px] px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
          />
          <input
            type="date"
            value={goalTargetDate}
            onChange={e => setGoalTargetDate(e.target.value)}
            className="px-2.5 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
          />
          <button
            type="submit"
            disabled={savingGoal || !goalTitle.trim()}
            className="px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-40"
          >
            Add Goal
          </button>
        </form>

        {goals.length === 0 ? (
          <p className="text-xs text-[var(--vybe-text-secondary)] py-2">No goals added yet.</p>
        ) : (
          <div className="space-y-2.5">
            {goals.map(g => (
              <div key={g.id} className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-xs font-bold truncate">{g.title}</p>
                    {g.targetDate && (
                      <p className="text-[10px] text-[var(--vybe-text-muted)]">Target: {g.targetDate}</p>
                    )}
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => handleStepGoal(g, -25)}
                      className="px-2 py-1 rounded bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[10px] font-bold"
                    >
                      -25%
                    </button>
                    <span className="text-xs font-extrabold w-10 text-center">{g.progressPercent}%</span>
                    <button
                      type="button"
                      onClick={() => handleStepGoal(g, 25)}
                      className="px-2 py-1 rounded bg-[var(--vybe-accent)] text-white keep-white text-[10px] font-bold"
                    >
                      +25%
                    </button>
                  </div>
                </div>
                <div className="h-1.5 w-full rounded-full bg-[var(--vybe-surface)] overflow-hidden">
                  <div
                    className="h-full bg-[var(--vybe-accent)] transition-all"
                    style={{ width: `${g.progressPercent}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Growth Programs Preview */}
      <section className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] flex items-center justify-center shrink-0">
            <Compass className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-xs font-bold">Structured Growth Programs</h3>
            <p className="text-[11px] text-[var(--vybe-text-secondary)]">
              {programs.length > 0 ? `${programs.length} live community challenges & tracks` : 'Join or launch multi-day learning tracks.'}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={onOpenPrograms}
          className="px-3.5 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-bold shrink-0"
        >
          Open
        </button>
      </section>
    </div>
  );
};
