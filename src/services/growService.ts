import {
  FieldVisibility,
  GrowProfile,
  GrowProject,
  GrowRequest,
  GrowthProgram,
  Opportunity,
  OpportunityApplication,
  OpportunityCategory,
  PersonalGoal,
  PersonalHabit
} from '../types';
import {
  SUPABASE_URL,
  UUID_REGEX,
  authedFetch,
  buildFallbackMinimalUser,
  getOptionalRestHeaders,
  mapSupabaseProfileToUser,
  resolveMediaUrl,
  signPrivateResumeUrlIfNeeded,
  STORY_PROFILE_COLUMNS
} from './supabaseService';

// ---------------------------------------------------------------------------
// 1. Personal Habits & Goals (100% Supabase-backed, Zero localStorage)
// ---------------------------------------------------------------------------
export async function fetchMyHabits(userId: string): Promise<PersonalHabit[]> {
  if (!UUID_REGEX.test(userId)) return [];
  try {
    const today = new Date().toISOString().slice(0, 10);
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/personal_habits?user_id=eq.${encodeURIComponent(userId)}&is_active=eq.true&select=id,user_id,title,category,frequency,target_per_week,streak_days,best_streak,is_active,personal_habit_logs(log_date)&order=created_at.desc`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((h: any) => {
      const logs: Array<{ log_date: string }> = Array.isArray(h.personal_habit_logs) ? h.personal_habit_logs : [];
      return {
        id: h.id,
        userId: h.user_id,
        title: h.title || 'Daily Habit',
        category: h.category || 'Focus',
        cadence: h.frequency === 'WEEKLY' ? 'WEEKLY' : 'DAILY',
        targetPerWeek: Number(h.target_per_week || 7),
        currentStreak: Number(h.streak_days || 0),
        bestStreak: Number(h.best_streak || 0),
        isArchived: !h.is_active,
        completedToday: logs.some(l => String(l.log_date || '').slice(0, 10) === today)
      };
    });
  } catch {
    return [];
  }
}

export async function createMyHabit(params: {
  userId: string;
  title: string;
  category: string;
  cadence: 'DAILY' | 'WEEKLY';
}): Promise<boolean> {
  if (!UUID_REGEX.test(params.userId) || !params.title.trim()) return false;
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/personal_habits`,
      {
        method: 'POST',
        body: JSON.stringify({
          user_id: params.userId,
          title: params.title.trim(),
          category: params.category.trim() || 'Mindfulness',
          frequency: params.cadence,
          target_per_week: params.cadence === 'DAILY' ? 7 : 3
        })
      },
      { Prefer: 'return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function toggleHabitToday(habit: PersonalHabit): Promise<boolean> {
  if (!UUID_REGEX.test(habit.id) || !UUID_REGEX.test(habit.userId)) return false;
  const today = new Date().toISOString().slice(0, 10);
  try {
    if (habit.completedToday) {
      const delRes = await authedFetch(
        `${SUPABASE_URL}/rest/v1/personal_habit_logs?habit_id=eq.${encodeURIComponent(habit.id)}&log_date=eq.${today}`,
        { method: 'DELETE' }
      );
      if (delRes.ok) {
        const nextStreak = Math.max(0, habit.currentStreak - 1);
        await authedFetch(`${SUPABASE_URL}/rest/v1/personal_habits?id=eq.${encodeURIComponent(habit.id)}`, {
          method: 'PATCH',
          body: JSON.stringify({ streak_days: nextStreak })
        });
      }
      return delRes.ok;
    } else {
      const addRes = await authedFetch(
        `${SUPABASE_URL}/rest/v1/personal_habit_logs?on_conflict=habit_id,log_date`,
        {
          method: 'POST',
          body: JSON.stringify({ habit_id: habit.id, user_id: habit.userId, log_date: today })
        },
        { Prefer: 'resolution=ignore-duplicates,return=minimal' }
      );
      if (addRes.ok) {
        const nextStreak = habit.currentStreak + 1;
        await authedFetch(`${SUPABASE_URL}/rest/v1/personal_habits?id=eq.${encodeURIComponent(habit.id)}`, {
          method: 'PATCH',
          body: JSON.stringify({
            streak_days: nextStreak,
            best_streak: Math.max(habit.bestStreak, nextStreak)
          })
        });
      }
      return addRes.ok;
    }
  } catch {
    return false;
  }
}

export async function fetchMyGoals(userId: string): Promise<PersonalGoal[]> {
  if (!UUID_REGEX.test(userId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/personal_goals?user_id=eq.${encodeURIComponent(userId)}&select=id,user_id,title,description,category,target_date,progress_pct,status,milestones_json&order=created_at.desc`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((g: any) => ({
      id: g.id,
      userId: g.user_id,
      title: g.title || 'Goal',
      description: g.description || '',
      category: g.category || 'Career',
      targetDate: g.target_date || undefined,
      progressPercent: Number(g.progress_pct || 0),
      status: (g.status || 'ACTIVE') as PersonalGoal['status'],
      milestones: Array.isArray(g.milestones_json) ? g.milestones_json : []
    }));
  } catch {
    return [];
  }
}

export async function createMyGoal(params: {
  userId: string;
  title: string;
  description: string;
  category: string;
  targetDate?: string;
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/personal_goals`,
      {
        method: 'POST',
        body: JSON.stringify({
          user_id: params.userId,
          title: params.title.trim(),
          description: params.description.trim(),
          category: params.category.trim() || 'Career',
          target_date: params.targetDate || null,
          progress_pct: 0,
          status: 'ACTIVE'
        })
      },
      { Prefer: 'return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function updateMyGoalProgress(goalId: string, progressPercent: number): Promise<boolean> {
  const clamped = Math.max(0, Math.min(100, Math.round(progressPercent)));
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/personal_goals?id=eq.${encodeURIComponent(goalId)}`, {
      method: 'PATCH',
      body: JSON.stringify({
        progress_pct: clamped,
        status: clamped >= 100 ? 'COMPLETED' : 'ACTIVE'
      })
    });
    return res.ok;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 2. Growth Programs, Enrollment & Task Logs
// ---------------------------------------------------------------------------
export async function fetchGrowthPrograms(myUserId?: string): Promise<GrowthProgram[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/growth_programs?is_published=eq.true&select=id,owner_id,title,subtitle,description,category,duration_days,cover_url,tags,members_count,created_at,profiles:owner_id(${STORY_PROFILE_COLUMNS}),growth_program_tasks(id,program_id,day_number,title,instructions,task_type,sort_order),growth_program_members(user_id,status,progress_pct)&order=members_count.desc&limit=40`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];

    let completedTaskIds = new Set<string>();
    if (myUserId && UUID_REGEX.test(myUserId)) {
      const logRes = await authedFetch(
        `${SUPABASE_URL}/rest/v1/growth_program_task_logs?user_id=eq.${encodeURIComponent(myUserId)}&select=task_id`
      ).catch(() => null);
      if (logRes && logRes.ok) {
        const logs = await logRes.json().catch(() => []);
        if (Array.isArray(logs)) {
          completedTaskIds = new Set(logs.map((l: any) => l.task_id).filter(Boolean));
        }
      }
    }

    return rows.map((p: any) => {
      const members: Array<{ user_id: string; status: string; progress_pct: number }> = Array.isArray(p.growth_program_members)
        ? p.growth_program_members
        : [];
      const myMem = myUserId ? members.find(m => m.user_id === myUserId && m.status !== 'REMOVED') : undefined;
      const rawTasks = Array.isArray(p.growth_program_tasks) ? p.growth_program_tasks : [];
      const tasks = rawTasks
        .sort((a: any, b: any) => Number(a.sort_order || a.day_number || 0) - Number(b.sort_order || b.day_number || 0))
        .map((t: any) => ({
          id: t.id,
          programId: t.program_id,
          title: t.title || 'Program Step',
          description: t.instructions || '',
          sortOrder: Number(t.day_number || t.sort_order || 1),
          points: 10,
          isCompletedByMe: completedTaskIds.has(t.id)
        }));
      const completedCount = tasks.filter((t: any) => t.isCompletedByMe).length;
      const computedPct = tasks.length > 0 ? Math.round((completedCount / tasks.length) * 100) : myMem?.progress_pct || 0;

      return {
        id: p.id,
        creatorId: p.owner_id,
        creator: p.profiles ? mapSupabaseProfileToUser(p.profiles) : buildFallbackMinimalUser(p.owner_id),
        title: p.title || 'Growth Program',
        description: p.description || p.subtitle || '',
        category: p.category || 'Personal Growth',
        difficulty: 'INTERMEDIATE',
        durationDays: Number(p.duration_days || 14),
        coverUrl: p.cover_url ? resolveMediaUrl(p.cover_url) : undefined,
        participantsCount: Number(p.members_count || 1),
        isFeatured: false,
        isEnrolled: Boolean(myMem),
        progressPercent: computedPct,
        tasks
      };
    });
  } catch {
    return [];
  }
}

export async function createGrowthProgramInSupabase(params: {
  ownerId: string;
  title: string;
  description: string;
  category: string;
  durationDays: number;
  coverUrl?: string;
  taskTitles: string[];
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/growth_programs`,
      {
        method: 'POST',
        body: JSON.stringify({
          owner_id: params.ownerId,
          title: params.title.trim(),
          description: params.description.trim(),
          category: params.category.trim() || 'Personal Growth',
          duration_days: Math.max(1, Math.min(365, params.durationDays || 14)),
          cover_url: params.coverUrl || null,
          is_published: true
        })
      },
      { Prefer: 'return=representation' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Failed to create program.' };
    }
    const rows = await res.json();
    const prog = Array.isArray(rows) ? rows[0] : rows;
    if (prog?.id) {
      await authedFetch(
        `${SUPABASE_URL}/rest/v1/growth_program_members`,
        {
          method: 'POST',
          body: JSON.stringify({ program_id: prog.id, user_id: params.ownerId, role: 'OWNER', status: 'ACTIVE' })
        },
        { Prefer: 'return=minimal' }
      ).catch(() => null);

      const tasksPayload = params.taskTitles
        .map(t => t.trim())
        .filter(Boolean)
        .map((title, idx) => ({
          program_id: prog.id,
          day_number: idx + 1,
          sort_order: idx + 1,
          title,
          instructions: ''
        }));
      if (tasksPayload.length > 0) {
        await authedFetch(
          `${SUPABASE_URL}/rest/v1/growth_program_tasks`,
          { method: 'POST', body: JSON.stringify(tasksPayload) },
          { Prefer: 'return=minimal' }
        ).catch(() => null);
      }
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function enrollInGrowthProgram(programId: string, userId: string): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/growth_program_members?on_conflict=program_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify({ program_id: programId, user_id: userId, role: 'MEMBER', status: 'ACTIVE' })
      },
      { Prefer: 'resolution=merge-duplicates,return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function toggleProgramTaskComplete(taskId: string, userId: string, completed: boolean): Promise<boolean> {
  try {
    if (completed) {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/growth_program_task_logs?on_conflict=task_id,user_id`,
        { method: 'POST', body: JSON.stringify({ task_id: taskId, user_id: userId }) },
        { Prefer: 'resolution=ignore-duplicates,return=minimal' }
      );
      return res.ok;
    }
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/growth_program_task_logs?task_id=eq.${encodeURIComponent(taskId)}&user_id=eq.${encodeURIComponent(userId)}`,
      { method: 'DELETE' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 3. Professional GROW Profiles (Per-Field Privacy) & Requests
// ---------------------------------------------------------------------------
function mapGrowProfileRow(r: any): GrowProfile {
  return {
    userId: r.user_id,
    headline: r.headline || '',
    currentRole: r.current_role || '',
    companyOrSchool: r.company_or_org || '',
    location: r.location || '',
    skills: Array.isArray(r.skills) ? r.skills : [],
    interests: Array.isArray(r.interests) ? r.interests : [],
    educationJson: Array.isArray(r.education_json) ? r.education_json : [],
    experienceJson: Array.isArray(r.experience_json) ? r.experience_json : [],
    achievements: Array.isArray(r.achievements_json)
      ? r.achievements_json.map((a: any) => (typeof a === 'string' ? a : a?.title || ''))
      : [],
    portfolioLinks: Array.isArray(r.portfolio_links_json) ? r.portfolio_links_json : [],
    openToCollaborate:
      r.availability_status === 'COLLABORATING' ||
      (Array.isArray(r.availability_modes) && r.availability_modes.includes('COLLABORATE')),
    openToMentor:
      r.availability_status === 'MENTORING' ||
      (Array.isArray(r.availability_modes) && r.availability_modes.includes('MENTOR')),
    openToHire:
      r.availability_status === 'HIRING' ||
      (Array.isArray(r.availability_modes) && r.availability_modes.includes('HIRE')),
    isPublicDiscoverable: true,
    headlineVisibility: (r.visibility_role || 'PUBLIC') as FieldVisibility,
    skillsVisibility: (r.visibility_company || 'PUBLIC') as FieldVisibility,
    educationVisibility: (r.visibility_education || 'PUBLIC') as FieldVisibility,
    experienceVisibility: (r.visibility_experience || 'PUBLIC') as FieldVisibility,
    linksVisibility: (r.visibility_portfolio || 'PUBLIC') as FieldVisibility,
    user: r.profiles ? mapSupabaseProfileToUser(r.profiles) : undefined
  };
}

export async function fetchDiscoverableGrowProfiles(): Promise<GrowProfile[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/grow_profiles?select=user_id,headline,current_role,company_or_org,location,skills,interests,availability_status,availability_modes,visibility_role,visibility_company,visibility_education,visibility_experience,visibility_portfolio,profiles:user_id(${STORY_PROFILE_COLUMNS})&order=updated_at.desc&limit=40`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    return Array.isArray(rows) ? rows.map(mapGrowProfileRow) : [];
  } catch {
    return [];
  }
}

export async function fetchVisibleGrowProfileRpc(targetUserId: string): Promise<GrowProfile | null> {
  if (!UUID_REGEX.test(targetUserId)) return null;
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/get_visible_grow_profile`, {
      method: 'POST',
      body: JSON.stringify({ p_target_user_id: targetUserId })
    });
    if (!res.ok) return null;
    const data = await res.json();
    return data?.user_id ? mapGrowProfileRow(data) : null;
  } catch {
    return null;
  }
}

export async function upsertMyGrowProfile(profile: GrowProfile): Promise<{ ok: boolean; error?: string }> {
  if (!UUID_REGEX.test(profile.userId)) return { ok: false, error: 'Valid account required.' };
  const modes: string[] = [];
  if (profile.openToCollaborate) modes.push('COLLABORATE');
  if (profile.openToMentor) modes.push('MENTOR');
  if (profile.openToHire) modes.push('HIRE');

  const availabilityStatus = profile.openToMentor
    ? 'MENTORING'
    : profile.openToHire
      ? 'HIRING'
      : profile.openToCollaborate
        ? 'COLLABORATING'
        : 'OPEN_TO_NETWORK';

  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/grow_profiles?on_conflict=user_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          user_id: profile.userId,
          headline: profile.headline.trim(),
          current_role: profile.currentRole.trim(),
          company_or_org: profile.companyOrSchool.trim(),
          location: profile.location.trim(),
          skills: profile.skills,
          interests: profile.interests,
          education_json: profile.educationJson,
          experience_json: profile.experienceJson,
          achievements_json: profile.achievements,
          portfolio_links_json: profile.portfolioLinks,
          availability_status: availabilityStatus,
          availability_modes: modes,
          visibility_role: profile.headlineVisibility,
          visibility_company: profile.skillsVisibility,
          visibility_location: profile.headlineVisibility,
          visibility_education: profile.educationVisibility,
          visibility_experience: profile.experienceVisibility,
          visibility_portfolio: profile.linksVisibility
        })
      },
      { Prefer: 'resolution=merge-duplicates,return=minimal' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Could not save GROW profile.' };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function fetchMyGrowRequests(userId: string): Promise<GrowRequest[]> {
  if (!UUID_REGEX.test(userId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/grow_requests?or=(sender_id.eq.${userId},recipient_id.eq.${userId})&select=id,sender_id,recipient_id,request_type,message,status,created_at,sender:sender_id(${STORY_PROFILE_COLUMNS}),recipient:recipient_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=50`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      senderId: r.sender_id,
      recipientId: r.recipient_id,
      requestType: (r.request_type || 'CONNECT') as GrowRequest['requestType'],
      note: r.message || '',
      status: (r.status || 'PENDING') as GrowRequest['status'],
      createdAtIso: r.created_at || new Date().toISOString(),
      sender: r.sender ? mapSupabaseProfileToUser(r.sender) : buildFallbackMinimalUser(r.sender_id),
      recipient: r.recipient ? mapSupabaseProfileToUser(r.recipient) : buildFallbackMinimalUser(r.recipient_id)
    }));
  } catch {
    return [];
  }
}

export async function sendGrowRequestInSupabase(params: {
  senderId: string;
  recipientId: string;
  requestType: 'CONNECT' | 'MENTORSHIP';
  note: string;
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/grow_requests`,
      {
        method: 'POST',
        body: JSON.stringify({
          sender_id: params.senderId,
          recipient_id: params.recipientId,
          request_type: params.requestType,
          message: params.note.trim().slice(0, 300),
          status: 'PENDING'
        })
      },
      { Prefer: 'return=minimal' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Request failed.' };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function respondGrowRequestInSupabase(requestId: string, accept: boolean): Promise<boolean> {
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/grow_requests?id=eq.${encodeURIComponent(requestId)}`, {
      method: 'PATCH',
      body: JSON.stringify({
        status: accept ? 'ACCEPTED' : 'DECLINED',
        responded_at: new Date().toISOString()
      })
    });
    return res.ok;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 4. Showcase Projects & Interactions
// ---------------------------------------------------------------------------
export async function fetchGrowProjects(myUserId?: string): Promise<GrowProject[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/projects?select=id,owner_id,title,summary,description,category,tech_stack,media_urls,repo_url,demo_url,needs_roles,likes_count,bookmarks_count,created_at,profiles:owner_id(${STORY_PROFILE_COLUMNS}),project_interactions(user_id,is_liked,is_bookmarked)&order=created_at.desc&limit=40`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((p: any) => {
      const inters: Array<{ user_id: string; is_liked: boolean; is_bookmarked: boolean }> = Array.isArray(
        p.project_interactions
      )
        ? p.project_interactions
        : [];
      const mine = myUserId ? inters.find(i => i.user_id === myUserId) : undefined;
      return {
        id: p.id,
        authorId: p.owner_id,
        author: p.profiles ? mapSupabaseProfileToUser(p.profiles) : buildFallbackMinimalUser(p.owner_id),
        title: p.title || 'Project',
        summary: p.summary || p.description || '',
        category: p.category || 'Product',
        tags: Array.isArray(p.tech_stack) ? p.tech_stack : [],
        mediaUrls: Array.isArray(p.media_urls) ? p.media_urls.map((u: string) => resolveMediaUrl(u)) : [],
        demoUrl: p.demo_url || undefined,
        repoUrl: p.repo_url || undefined,
        seekingRoles: Array.isArray(p.needs_roles) ? p.needs_roles : [],
        likesCount: Number(p.likes_count || 0),
        bookmarksCount: Number(p.bookmarks_count || 0),
        isLikedByMe: Boolean(mine?.is_liked),
        isBookmarkedByMe: Boolean(mine?.is_bookmarked),
        createdAtIso: p.created_at || new Date().toISOString()
      };
    });
  } catch {
    return [];
  }
}

export async function createGrowProjectInSupabase(params: {
  ownerId: string;
  title: string;
  summary: string;
  category: string;
  tags: string[];
  mediaUrls: string[];
  demoUrl?: string;
  repoUrl?: string;
  seekingRoles: string[];
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/projects`,
      {
        method: 'POST',
        body: JSON.stringify({
          owner_id: params.ownerId,
          title: params.title.trim(),
          summary: params.summary.trim(),
          description: params.summary.trim(),
          category: params.category.trim() || 'Product',
          tech_stack: params.tags,
          media_urls: params.mediaUrls,
          demo_url: params.demoUrl?.trim() || '',
          repo_url: params.repoUrl?.trim() || '',
          looking_for_collaborators: params.seekingRoles.length > 0,
          needs_roles: params.seekingRoles
        })
      },
      { Prefer: 'return=minimal' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Could not publish project.' };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function toggleProjectInteractionInSupabase(params: {
  projectId: string;
  userId: string;
  isLiked: boolean;
  isBookmarked: boolean;
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/project_interactions?on_conflict=project_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          project_id: params.projectId,
          user_id: params.userId,
          is_liked: params.isLiked,
          is_bookmarked: params.isBookmarked,
          updated_at: new Date().toISOString()
        })
      },
      { Prefer: 'resolution=merge-duplicates,return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 5. Verified Opportunities (10 Categories) & Strictly Private Resumes
// ---------------------------------------------------------------------------
function normalizeOpportunityCategory(raw: any): OpportunityCategory {
  const upper = String(raw || 'FULL_TIME_JOBS').toUpperCase();
  if (upper === 'JOB' || upper === 'FULL_TIME_JOBS') return 'FULL_TIME_JOBS';
  if (upper === 'PART_TIME_JOBS') return 'PART_TIME_JOBS';
  if (upper === 'INTERNSHIP' || upper === 'INTERNSHIPS') return 'INTERNSHIPS';
  if (upper === 'FREELANCE' || upper === 'FREELANCE_GIGS') return 'FREELANCE_GIGS';
  if (upper === 'PROJECTS') return 'PROJECTS';
  if (upper === 'COLLABORATIONS') return 'COLLABORATIONS';
  if (upper === 'HACKATHON' || upper === 'HACKATHONS') return 'HACKATHONS';
  if (upper === 'GRANT' || upper === 'GRANTS_FELLOWSHIPS') return 'GRANTS_FELLOWSHIPS';
  if (upper === 'RESEARCH') return 'RESEARCH';
  if (upper === 'MENTORSHIP') return 'MENTORSHIP';
  return 'FULL_TIME_JOBS';
}

export async function fetchOpportunities(myUserId?: string): Promise<Opportunity[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/opportunities?status=eq.OPEN&select=id,posted_by,title,organization_name,description,category,work_mode,location,compensation_text,expires_at,source_url,skills_required,status,created_at,profiles:posted_by(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=60`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];

    let appliedSet = new Set<string>();
    if (myUserId && UUID_REGEX.test(myUserId)) {
      const appRes = await authedFetch(
        `${SUPABASE_URL}/rest/v1/opportunity_applications?applicant_id=eq.${encodeURIComponent(myUserId)}&select=opportunity_id`
      ).catch(() => null);
      if (appRes && appRes.ok) {
        const appRows = await appRes.json().catch(() => []);
        if (Array.isArray(appRows)) {
          appliedSet = new Set(appRows.map((a: any) => a.opportunity_id).filter(Boolean));
        }
      }
    }

    return rows.map((o: any) => ({
      id: o.id,
      posterId: o.posted_by,
      poster: o.profiles ? mapSupabaseProfileToUser(o.profiles) : buildFallbackMinimalUser(o.posted_by),
      title: o.title || 'Opportunity',
      organization: o.organization_name || 'Organization',
      description: o.description || '',
      category: normalizeOpportunityCategory(o.category),
      workMode: o.work_mode === 'HYBRID' ? 'HYBRID' : o.work_mode === 'ONSITE' ? 'ON_SITE' : 'REMOTE',
      location: o.location || 'Remote',
      compensation: o.compensation_text || undefined,
      deadline: o.expires_at ? new Date(o.expires_at).toLocaleDateString() : undefined,
      applyUrl: o.source_url || undefined,
      skillsRequired: Array.isArray(o.skills_required) ? o.skills_required : [],
      applicantsCount: 0,
      hasApplied: appliedSet.has(o.id),
      isClosed: o.status !== 'OPEN',
      createdAtIso: o.created_at || new Date().toISOString()
    }));
  } catch {
    return [];
  }
}

export async function createOpportunityInSupabase(params: {
  posterId: string;
  title: string;
  organization: string;
  description: string;
  category: OpportunityCategory;
  workMode: 'REMOTE' | 'HYBRID' | 'ON_SITE';
  location: string;
  compensation?: string;
  applyUrl?: string;
  skillsRequired: string[];
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const dbWorkMode = params.workMode === 'ON_SITE' ? 'ONSITE' : params.workMode;
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/opportunities`,
      {
        method: 'POST',
        body: JSON.stringify({
          posted_by: params.posterId,
          title: params.title.trim(),
          organization_name: params.organization.trim(),
          description: params.description.trim(),
          category: params.category,
          work_mode: dbWorkMode,
          location: params.location.trim() || 'Remote',
          compensation_text: params.compensation?.trim() || '',
          source_url: params.applyUrl?.trim() || '',
          skills_required: params.skillsRequired
        })
      },
      { Prefer: 'return=minimal' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Failed to publish opportunity.' };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function submitOpportunityApplication(params: {
  opportunityId: string;
  applicantId: string;
  coverNote: string;
  resumeStoragePath?: string;
  portfolioUrl?: string;
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/opportunity_applications`,
      {
        method: 'POST',
        body: JSON.stringify({
          opportunity_id: params.opportunityId,
          applicant_id: params.applicantId,
          note: params.coverNote.trim(),
          resume_url: params.resumeStoragePath || '',
          portfolio_url: params.portfolioUrl?.trim() || '',
          status: 'SUBMITTED'
        })
      },
      { Prefer: 'return=minimal' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Application submission failed.' };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function fetchApplicationsForOpportunity(opportunityId: string): Promise<OpportunityApplication[]> {
  if (!UUID_REGEX.test(opportunityId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/opportunity_applications?opportunity_id=eq.${encodeURIComponent(opportunityId)}&select=id,opportunity_id,applicant_id,note,resume_url,portfolio_url,status,created_at,profiles:applicant_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return Promise.all(
      rows.map(async (r: any) => ({
        id: r.id,
        opportunityId: r.opportunity_id,
        applicantId: r.applicant_id,
        applicant: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(r.applicant_id),
        coverNote: r.note || '',
        resumeUrl: r.resume_url ? await signPrivateResumeUrlIfNeeded(r.resume_url) : undefined,
        portfolioUrl: r.portfolio_url || undefined,
        status: (r.status || 'SUBMITTED') as OpportunityApplication['status'],
        createdAtIso: r.created_at || new Date().toISOString()
      }))
    );
  } catch {
    return [];
  }
}

export async function reportOpportunityInSupabase(params: {
  opportunityId: string;
  reporterId: string;
  reason: string;
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/opportunity_reports`,
      {
        method: 'POST',
        body: JSON.stringify({
          opportunity_id: params.opportunityId,
          reporter_id: params.reporterId,
          reason: params.reason.trim().slice(0, 400)
        })
      },
      { Prefer: 'return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}
