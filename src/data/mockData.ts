import { 
  User, Post, Story, CrossedPathEncounter, TimeCapsule, 
  UnloadRoom, VybeShop, Community, SpiritualReflection, Conversation
} from '../types';

export const CURRENT_USER: User = {
  id: 'usr_me_01',
  username: 'nimair',
  displayName: 'Nimair',
  publicVybeId: 'VYBE-9801A',
  bio: 'Building the next evolution of social discovery & mood spaces ⚡ Indie maker, music lover & visual storyteller.',
  avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80',
  coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80',
  isVerified: true,
  isVip: true,
  isVerifiedBusiness: false,
  isWorker: false,
  followersCount: 1420,
  followingCount: 489,
  postsCount: 18,
  currentMood: 'CHILL',
  livePulse: 'Feeling creative',
  isOnline: true,
  badges: ['VERIFIED_BLUE', 'VIP_GOLD'],
  joinedDate: 'Joined March 2026',
  location: 'Mumbai, India',
};

export const SEED_USERS: User[] = [
  CURRENT_USER,
  {
    id: 'usr_priya_02',
    username: 'priya_k',
    displayName: 'Priya Kapoor',
    publicVybeId: 'VYBE-4412B',
    bio: 'Architect by day, acoustic guitarist by night 🎸 Coffee enthusiast.',
    avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=400&q=80',
    coverUrl: 'https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1200&q=80',
    isVerified: true,
    isVip: false,
    isVerifiedBusiness: false,
    isWorker: false,
    followersCount: 3200,
    followingCount: 612,
    postsCount: 42,
    currentMood: 'HAPPY',
    livePulse: 'Listening to music',
    isOnline: true,
    badges: ['VERIFIED_BLUE'],
    joinedDate: 'Joined January 2026',
    location: 'Bengaluru, India',
    isFollowedByMe: true,
  },
  {
    id: 'usr_rohan_03',
    username: 'rohan_creates',
    displayName: 'Rohan Verma',
    publicVybeId: 'VYBE-7821C',
    bio: 'Visual designer & cinematic reel creator. Chasing moody neon streetlights.',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80',
    coverUrl: 'https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=1200&q=80',
    isVerified: true,
    isVip: true,
    isVerifiedBusiness: false,
    isWorker: false,
    followersCount: 8900,
    followingCount: 310,
    postsCount: 94,
    currentMood: 'FUN',
    livePulse: 'Open to chatting',
    isOnline: true,
    badges: ['VERIFIED_BLUE', 'VIP_GOLD'],
    joinedDate: 'Joined December 2025',
    location: 'Delhi NCR',
    isFollowedByMe: true,
  },
  {
    id: 'usr_maya_04',
    username: 'maya_zen',
    displayName: 'Maya Sen',
    publicVybeId: 'VYBE-1109D',
    bio: 'Mindfulness coach & breathwork practitioner. Small steps create massive shifts 🌱',
    avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=400&q=80',
    coverUrl: 'https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=1200&q=80',
    isVerified: true,
    isVip: false,
    isVerifiedBusiness: false,
    isWorker: false,
    followersCount: 15400,
    followingCount: 220,
    postsCount: 128,
    currentMood: 'HEALING',
    livePulse: 'Taking quiet time',
    isOnline: false,
    badges: ['VERIFIED_BLUE'],
    joinedDate: 'Joined February 2026',
    location: 'Goa, India',
    isFollowedByMe: false,
  },
  {
    id: 'usr_roaster_05',
    username: 'subko_roasters',
    displayName: 'Subko Coffee Roasters',
    publicVybeId: 'VYBE-8822S',
    bio: 'Artisan craft coffee, bean to bar chocolate & bakery. Direct from Indian estates ☕',
    avatarUrl: 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=400&q=80',
    coverUrl: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1200&q=80',
    isVerified: true,
    isVip: false,
    isVerifiedBusiness: true,
    isWorker: false,
    followersCount: 24500,
    followingCount: 88,
    postsCount: 112,
    currentMood: 'CHILL',
    livePulse: 'Open to chatting',
    isOnline: true,
    badges: ['VERIFIED_BLUE', 'SHOP_GOLD'],
    joinedDate: 'Joined November 2025',
    location: 'Bandra West, Mumbai',
    isFollowedByMe: true,
  },
  {
    id: 'usr_vybeteam_06',
    username: 'vybe_staff',
    displayName: 'VYBE Team Official',
    publicVybeId: 'VYBE-0001HQ',
    bio: 'Official Community Operations & Platform Integrity Team for VYBE. Here to assist and protect our community.',
    avatarUrl: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=400&q=80',
    coverUrl: 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=1200&q=80',
    isVerified: true,
    isVip: false,
    isVerifiedBusiness: false,
    isWorker: true,
    followersCount: 84000,
    followingCount: 45,
    postsCount: 52,
    currentMood: 'GENERAL',
    livePulse: 'Open to chatting',
    isOnline: true,
    badges: ['VERIFIED_BLUE', 'WORKER_GREEN'],
    joinedDate: 'Joined Foundation 2025',
    location: 'HQ',
  }
];

export const INITIAL_POSTS: Post[] = [
  {
    id: 'post_01',
    author: SEED_USERS[2], // Rohan
    caption: 'Midnight rain over Bandra sea link with Cyberpunk tones 🌃 Nothing feels quite like this city at 2 AM when the traffic slows down.',
    mediaUrls: ['https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=1000&q=80'],
    mediaType: 'IMAGE',
    timestamp: '2h ago',
    likesCount: 142,
    commentsCount: 18,
    savesCount: 34,
    sharesCount: 12,
    viewsCount: 1280,
    isLiked: true,
    isSaved: true,
    tags: ['mumbai', 'cyberpunk', 'citylights', 'chillvibes'],
    moodZone: 'CHILL',
    audioTrackTitle: 'Midnight Drive — Lo-Fi Chillwave',
    locationName: 'Bandra Bandstand, Mumbai',
    musicAttachment: {
      trackId: 'trk_01',
      title: 'Midnight Drive',
      artist: 'Kavinsky & Lo-fi Beats',
      previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3'
    },
    userReaction: 'FLAME',
    reactionsCount: {
      LOVE: 58,
      FLAME: 62,
      LAUGH: 4,
      WOW: 16,
      SAD: 2
    },
    comments: [
      {
        id: 'c_01',
        postId: 'post_01',
        author: SEED_USERS[1],
        content: 'The color grading is absolutely immaculate Rohan! What LUT did you use?',
        timestamp: '1h ago',
        likesCount: 6,
        isLiked: true
      },
      {
        id: 'c_02',
        postId: 'post_01',
        author: CURRENT_USER,
        content: 'Such a vibe. That sea link stretch in rainy weather is unmatched.',
        timestamp: '45m ago',
        likesCount: 3
      }
    ]
  },
  {
    id: 'post_02',
    author: SEED_USERS[1], // Priya
    caption: 'Finally played the full acoustic arrangement of "Roz Roz" at the rooftop session 🎸 Grateful for everyone who sang along in the golden hour light ✨',
    mediaUrls: ['https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=1000&q=80'],
    mediaType: 'IMAGE',
    timestamp: '4h ago',
    likesCount: 284,
    commentsCount: 39,
    savesCount: 45,
    sharesCount: 22,
    viewsCount: 2450,
    isLiked: false,
    isSaved: false,
    tags: ['acoustic', 'livemusic', 'happyvibes', 'sunset'],
    moodZone: 'HAPPY',
    audioTrackTitle: 'Roz Roz (Acoustic Unplugged) — Priya K',
    locationName: 'Indiranagar 100ft Rd, Bengaluru',
    musicAttachment: {
      trackId: 'trk_02',
      title: 'Roz Roz Acoustic',
      artist: 'The Yellow Diary & Priya K',
      previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3'
    },
    userReaction: undefined,
    reactionsCount: {
      LOVE: 198,
      FLAME: 42,
      LAUGH: 12,
      WOW: 30,
      SAD: 2
    },
    comments: [
      {
        id: 'c_03',
        postId: 'post_02',
        author: SEED_USERS[3], // Maya
        content: 'Your voice brought pure peace to the room Priya. Keep shining! ✨',
        timestamp: '3h ago',
        likesCount: 14
      }
    ]
  },
  {
    id: 'post_03',
    author: SEED_USERS[3], // Maya Sen
    caption: 'It is okay if all you did today was breathe through the chaos. Healing is never a straight line, it is a spiral that always rises. Be gentle with your thoughts 🌱',
    mediaUrls: ['https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=1000&q=80'],
    mediaType: 'IMAGE',
    timestamp: '6h ago',
    likesCount: 412,
    commentsCount: 52,
    savesCount: 180,
    sharesCount: 88,
    viewsCount: 3890,
    isLiked: true,
    isSaved: true,
    tags: ['healing', 'mindfulness', 'selfcare', 'reset'],
    moodZone: 'HEALING',
    audioTrackTitle: 'Tibetan Singing Bowls & Morning Stream',
    locationName: 'Arambol, Goa',
    userReaction: 'LOVE',
    reactionsCount: {
      LOVE: 320,
      FLAME: 15,
      LAUGH: 0,
      WOW: 45,
      SAD: 32
    },
    comments: [
      {
        id: 'c_04',
        postId: 'post_03',
        author: CURRENT_USER,
        content: 'Needed to read this exact message today. Thank you Maya.',
        timestamp: '5h ago',
        likesCount: 8
      }
    ]
  },
  {
    id: 'post_04',
    author: SEED_USERS[4], // Subko Roasters
    caption: 'Fresh lot of Kelagur Estate washed Arabica just landed! Tasting notes: wild blackberry, roasted hazelnuts & dark chocolate honeycomb. Come try our manual brew bar this weekend ☕✨',
    mediaUrls: ['https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1000&q=80'],
    mediaType: 'IMAGE',
    timestamp: '1d ago',
    likesCount: 520,
    commentsCount: 64,
    savesCount: 112,
    sharesCount: 30,
    viewsCount: 4600,
    isLiked: true,
    isSaved: false,
    tags: ['specialtycoffee', 'roasters', 'mumbaifoodie', 'subko'],
    moodZone: 'CHILL',
    locationName: 'Subko Crafters, Bandra West',
    userReaction: 'LOVE',
    reactionsCount: {
      LOVE: 340,
      FLAME: 95,
      LAUGH: 5,
      WOW: 78,
      SAD: 2
    },
    comments: []
  }
];

export const INITIAL_STORIES: Story[] = [
  {
    id: 'st_01',
    author: SEED_USERS[1], // Priya
    mediaUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=800&q=80',
    caption: 'Soundcheck before the acoustic sunset gig! 🎸',
    mediaType: 'PHOTO',
    audience: 'Public',
    musicTrackTitle: 'Acoustic Dreams — Priya K',
    timestamp: '2h ago',
    expiresHoursRemaining: 22,
    isViewed: false,
    reactionsCount: 48,
    isLikedByMe: true
  },
  {
    id: 'st_02',
    author: SEED_USERS[2], // Rohan
    mediaUrl: 'https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=800&q=80',
    caption: 'Testing new anamorphic lens in Old Delhi alleys 🔥',
    mediaType: 'PHOTO',
    audience: 'Public',
    musicTrackTitle: 'Delhi Neon Beats',
    timestamp: '4h ago',
    expiresHoursRemaining: 20,
    isViewed: false,
    reactionsCount: 92,
    isLikedByMe: false
  },
  {
    id: 'st_03',
    author: SEED_USERS[3], // Maya
    mediaUrl: 'https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=800&q=80',
    caption: 'Morning meditation with ocean breeze 🌊 Take a deep breath.',
    mediaType: 'PHOTO',
    audience: 'Public',
    musicTrackTitle: 'Ocean Calming Wave',
    timestamp: '5h ago',
    expiresHoursRemaining: 19,
    isViewed: false,
    reactionsCount: 120,
    isLikedByMe: false
  },
  {
    id: 'st_04',
    author: SEED_USERS[4], // Subko
    mediaUrl: 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=800&q=80',
    caption: 'Cold brew batch steeping for 24 hours ☕ Drop by!',
    mediaType: 'PHOTO',
    audience: 'Public',
    timestamp: '7h ago',
    expiresHoursRemaining: 17,
    isViewed: false,
    reactionsCount: 84,
    isLikedByMe: false
  }
];

export const INITIAL_REELS: Post[] = [
  {
    id: 'reel_01',
    author: SEED_USERS[2],
    caption: 'POV: Exploring the rain-washed streets of Mumbai in neon tones 🌧️✨ Shot on Sony FX3 with vintage 50mm.',
    mediaUrls: ['https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=800&q=80'],
    mediaType: 'REEL',
    isVybesReel: true,
    timestamp: '1d ago',
    likesCount: 1840,
    commentsCount: 96,
    savesCount: 420,
    sharesCount: 160,
    viewsCount: 18400,
    isLiked: true,
    isSaved: true,
    tags: ['reels', 'cinematic', 'mumbai', 'neonvibes'],
    moodZone: 'CHILL',
    audioTrackTitle: 'Midnight Drive — Lo-Fi Chillwave',
    locationName: 'Bandra West, Mumbai',
    musicAttachment: {
      trackId: 'trk_01',
      title: 'Midnight Drive',
      artist: 'Kavinsky & Lo-fi Beats',
      previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3'
    },
    userReaction: 'FLAME',
    reactionsCount: {
      LOVE: 820,
      FLAME: 950,
      LAUGH: 20,
      WOW: 40,
      SAD: 10
    },
    comments: []
  },
  {
    id: 'reel_02',
    author: SEED_USERS[1],
    caption: 'Quick acoustic fingerpicking tutorial on open D tuning 🎸 Save this to practice later!',
    mediaUrls: ['https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=800&q=80'],
    mediaType: 'REEL',
    isVybesReel: true,
    timestamp: '2d ago',
    likesCount: 2310,
    commentsCount: 142,
    savesCount: 890,
    sharesCount: 310,
    viewsCount: 29500,
    isLiked: false,
    isSaved: false,
    tags: ['guitar', 'fingerstyle', 'musictutorial', 'acoustic'],
    moodZone: 'HAPPY',
    audioTrackTitle: 'Roz Roz (Acoustic Unplugged) — Priya K',
    locationName: 'Bengaluru, India',
    musicAttachment: {
      trackId: 'trk_02',
      title: 'Roz Roz Acoustic',
      artist: 'The Yellow Diary & Priya K',
      previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3'
    },
    userReaction: undefined,
    reactionsCount: {
      LOVE: 1700,
      FLAME: 410,
      LAUGH: 40,
      WOW: 150,
      SAD: 10
    },
    comments: []
  }
];

export const INITIAL_CROSSED_PATHS: CrossedPathEncounter[] = [
  {
    id: 'cp_01',
    type: 'MISSED_CONNECTION',
    title: 'Yellow tote bag reading Murakami at Blue Tokai',
    description: 'We were sitting at adjacent tables around 4:30 PM yesterday. You had a vintage yellow tote bag and were reading Kafka on the Shore while sipping a tonic cold brew. We made eye contact when the espresso machine steamed.',
    broadLocation: 'Bandra West, Mumbai',
    venueOrEventName: 'Blue Tokai Coffee Roasters (Pali Hill)',
    encounterDate: 'Yesterday, 4:30 PM',
    poster: SEED_USERS[2],
    isAnonymous: true,
    expiresHoursRemaining: 48,
    potentialMatchesCount: 3,
    hasSentRequest: false,
    requestStatus: 'PENDING',
    tags: ['coffee', 'books', 'bandra', 'murakami']
  },
  {
    id: 'cp_02',
    type: 'VENUE_EVENT',
    title: 'Indie rock crowd singing aloud at The Humming Tree tribute night',
    description: 'Anyone who stayed back until 11:30 PM shouting lyrics by the bar area — incredible crowd energy! Trying to reconnect with the group who talked about vinyl records.',
    broadLocation: 'Indiranagar, Bengaluru',
    venueOrEventName: 'Indie Vinyl & Live Soundstage',
    encounterDate: 'Saturday Night, 11:00 PM',
    poster: SEED_USERS[1],
    isAnonymous: false,
    expiresHoursRemaining: 64,
    potentialMatchesCount: 8,
    hasSentRequest: true,
    requestStatus: 'ACCEPTED',
    tags: ['livemusic', 'bengaluru', 'vinyl', 'indierock']
  },
  {
    id: 'cp_03',
    type: 'BROAD_NEARBY',
    title: 'Evening Sunset Runners at Marine Drive Promanade',
    description: 'Ran 10k from Nariman Point to Girgaon Chowpatty in the golden sunset breeze. The city looked cinematic.',
    broadLocation: 'South Mumbai',
    venueOrEventName: 'Marine Drive Promenade',
    encounterDate: 'Today, 6:15 PM',
    poster: CURRENT_USER,
    isAnonymous: false,
    expiresHoursRemaining: 72,
    potentialMatchesCount: 5,
    hasSentRequest: false,
    requestStatus: 'PENDING',
    tags: ['running', 'marinedrive', 'sunset', 'mumbai']
  }
];

export const INITIAL_TIME_CAPSULES: TimeCapsule[] = [
  {
    id: 'cap_01',
    title: 'Dear 2027 Me: Launching VYBE 3.0 & Creative Freedom',
    sender: CURRENT_USER,
    recipientSummary: 'To Myself (One Year From Now)',
    sealedMessage: 'Hey! Reading this in 2027? I hope you kept your promise of building authentic spaces without chasing vanity algorithms. Remember the nights spent perfecting the vibe zones and how much love went into this codebase. Keep making things that resonate.',
    unlockDateFormatted: 'Dec 31, 2026',
    unlockTimestampMs: Date.now() + 1000 * 60 * 60 * 24 * 60,
    isUnlocked: false,
    createdAtFormatted: 'Oct 2026',
    categoryTheme: 'Career Milestone & Dreams'
  },
  {
    id: 'cap_02',
    title: 'Goa Road Trip Crew: Sealed Memory Box',
    sender: SEED_USERS[1],
    recipientSummary: 'To @nimair, @rohan_creates, @priya_k',
    sealedMessage: 'Here is the playlist we screamed along to while driving down the Ghats under thunderstorm clouds. Never lose this friendship.',
    unlockDateFormatted: 'Unlocked Today!',
    unlockTimestampMs: Date.now() - 10000,
    isUnlocked: true,
    createdAtFormatted: 'July 2026',
    categoryTheme: 'Roadtrip Adventure'
  }
];

export const INITIAL_UNLOAD_ROOMS: UnloadRoom[] = [
  {
    id: 'room_01',
    roomCode: 'VYB-CALM-7812',
    title: 'Late Night Overthinking & Quiet Company',
    description: 'A no-pressure sanctuary for anyone with noisy thoughts before sleep. Share what is weighing on your mind, or mute your mic and simply listen.',
    topicTag: 'Late Night Overthinking',
    mode: 'Voice & Text',
    activeListenersCount: 28,
    isHostOnline: true,
    hostName: 'Maya Sen',
    safetyNotice: 'Zero voice recording. Conversations are ephemeral and reset every morning.',
    recentMessages: [
      {
        id: 'um_01',
        senderName: 'Quiet Listener',
        isAnonymous: true,
        text: 'It is comforting to know other people are awake fighting the same racing thoughts.',
        timestamp: '5m ago',
        isMe: false
      },
      {
        id: 'um_02',
        senderName: 'Nimair',
        isAnonymous: false,
        text: 'Drink some warm chamomile or water. Take three slow breaths.',
        timestamp: '2m ago',
        isMe: true
      }
    ]
  },
  {
    id: 'room_02',
    roomCode: 'VYB-WORK-9901',
    title: 'Tech & Startup Burnout Vent Room',
    description: 'Endless sprint deadlines, imposter syndrome, and screen fatigue. Drop your baggage at the door.',
    topicTag: 'Work Burnout',
    mode: 'Text Only',
    activeListenersCount: 42,
    isHostOnline: true,
    hostName: 'DevCommunity Mod',
    safetyNotice: 'Strict anti-harassment policy. Be respectful of confidentiality.',
    recentMessages: [
      {
        id: 'um_03',
        senderName: 'Anonymous Founder',
        isAnonymous: true,
        text: 'Sometimes the pressure to scale makes you forget why you loved building in the first place.',
        timestamp: '12m ago',
        isMe: false
      }
    ]
  },
  {
    id: 'room_03',
    roomCode: 'VYB-HEAL-3310',
    title: 'Post-Breakup Gentle Healing & Solidarity',
    description: 'When the apartment feels too quiet and memories keep flashing. You do not have to endure it completely alone.',
    topicTag: 'Post-Breakup Healing',
    mode: 'Voice & Text',
    activeListenersCount: 19,
    isHostOnline: true,
    hostName: 'Aarav Support',
    safetyNotice: 'Zero tolerance for unsolicited romantic approaches or hostility.',
    recentMessages: []
  }
];

export const SPIRITUAL_REFLECTIONS: SpiritualReflection[] = [
  {
    id: 'sp_01',
    quote: 'You have a right to perform your prescribed duty, but you are not entitled to the fruits of action. Never consider yourself the cause of results, nor be attached to inaction.',
    traditionOrAuthor: 'Bhagavad Gita 2.47',
    theme: 'Equanimity & Purpose',
    audioCue: 'Serene Temple Bells'
  },
  {
    id: 'sp_02',
    quote: 'Silence is not the absence of sound, but the presence of an awakening stillness inside your own chest.',
    traditionOrAuthor: 'Rumi',
    theme: 'Inner Peace',
    audioCue: 'Gentle Himalayan Flute'
  },
  {
    id: 'sp_03',
    quote: 'Peace comes from within. Do not seek it without.',
    traditionOrAuthor: 'Gautama Buddha',
    theme: 'Mindfulness',
    audioCue: 'Singing Bowls'
  }
];

export const INITIAL_SHOPS: VybeShop[] = [
  {
    id: 'shop_01',
    businessName: 'Subko Coffee Roasters',
    category: 'Artisan Coffee, Bakery & Craft Chocolate',
    description: 'From Indian shade-grown estates directly to your cup. Single origins, craft sourdough, and stone-ground bean-to-bar pod chocolate.',
    logoUrl: 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=400&q=80',
    coverUrl: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1200&q=80',
    rating: 4.9,
    reviewsCount: 340,
    isVerifiedBusiness: true,
    openingHours: '8:00 AM - 10:30 PM',
    locationAddress: 'Crafters Quarters, Pali Village, Bandra West, Mumbai',
    contactPhone: '+91 98200 44102',
    contactEmail: 'hello@subko.coffee',
    followersCount: 24500,
    catalog: [
      {
        id: 'cat_01',
        name: 'Kelagur Washed Estate Whole Beans (250g)',
        priceFormatted: '₹620',
        description: 'Tasting notes: blackberry jam, milk chocolate, roasted hazelnut. Light-medium roast.',
        imageUrl: 'https://images.unsplash.com/photo-1559056199-641a0ac8b55e?auto=format&fit=crop&w=500&q=80',
        inStock: true
      },
      {
        id: 'cat_02',
        name: 'Pod Cacao 72% Dark Single Origin (80g)',
        priceFormatted: '₹380',
        description: 'Hand-harvested Idukki Kerala cacao pod, stone ground for 72 hours.',
        imageUrl: 'https://images.unsplash.com/photo-1548907040-4baa42d10919?auto=format&fit=crop&w=500&q=80',
        inStock: true
      },
      {
        id: 'cat_03',
        name: 'Salted Butter Babka Loaf (Daily Fresh)',
        priceFormatted: '₹450',
        description: 'Laminated flaky brioche ribboned with 65% craft dark chocolate ganache.',
        imageUrl: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=500&q=80',
        inStock: true
      }
    ],
    reviews: [
      {
        id: 'sr_01',
        author: CURRENT_USER,
        ratingStars: 5,
        reviewText: 'Hands down the best manual pour-over experience in Mumbai. The team really understands estate sourcing!',
        timestamp: '1 week ago',
        ownerReply: 'Thank you Nimair! Glad you enjoyed the Kelagur harvest. See you again soon!'
      }
    ]
  }
];

export const INITIAL_COMMUNITIES: Community[] = [
  {
    id: 'comm_01',
    name: 'Tech & Indie Makers India',
    description: 'Designers, full-stack engineers, and indie builders shipping products and exchanging transparent metrics.',
    category: 'Technology & Startups',
    avatarUrl: 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=400&q=80',
    bannerUrl: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1200&q=80',
    membersCount: 3420,
    isJoined: true,
    tags: ['buildinpublic', 'startups', 'react', 'indie']
  },
  {
    id: 'comm_02',
    name: 'Indian Indie Music & Gig Seekers',
    description: 'Gig announcements, jamming partner discovery, track feedback, and concert crew hookups.',
    category: 'Music & Arts',
    avatarUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=400&q=80',
    bannerUrl: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?auto=format&fit=crop&w=1200&q=80',
    membersCount: 6810,
    isJoined: true,
    tags: ['indiemusic', 'gigs', 'live', 'jamming']
  },
  {
    id: 'comm_03',
    name: 'Mindfulness, Breath & Slow Living',
    description: 'Daily breath practices, restorative reflections, tea ceremonies, and honest vulnerability.',
    category: 'Wellness & Healing',
    avatarUrl: 'https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=400&q=80',
    bannerUrl: 'https://images.unsplash.com/photo-1518241353330-0f7941c2d9b5?auto=format&fit=crop&w=1200&q=80',
    membersCount: 4190,
    isJoined: false,
    tags: ['meditation', 'breathwork', 'zen', 'slowlife']
  }
];

export const INITIAL_CONVERSATIONS: Conversation[] = [
  {
    id: 'conv_01',
    peerUser: SEED_USERS[1], // Priya
    lastMessage: 'Let’s record the duet version of Roz Roz this weekend! 🎙️',
    lastMessageTimestamp: '15m ago',
    unreadCount: 1,
    chatTheme: 'CYBER_LUXE',
    messages: [
      {
        id: 'msg_01',
        senderId: 'usr_priya_02',
        text: 'Hey Nimair! Loved your latest post from Bandra sea link.',
        timestamp: '10:14 AM',
        isMe: false
      },
      {
        id: 'msg_02',
        senderId: 'usr_me_01',
        text: 'Thanks Priya! That acoustic snippet you posted is stuck in my head all day haha.',
        timestamp: '10:16 AM',
        isMe: true,
        reactions: ['❤️']
      },
      {
        id: 'msg_03',
        senderId: 'usr_priya_02',
        text: 'Let’s record the duet version of Roz Roz this weekend! 🎙️',
        timestamp: '10:20 AM',
        isMe: false
      }
    ]
  },
  {
    id: 'conv_02',
    peerUser: SEED_USERS[2], // Rohan
    lastMessage: 'Check out this LUT file I sent over.',
    lastMessageTimestamp: '2h ago',
    unreadCount: 0,
    chatTheme: 'NEON_VIOLET',
    messages: [
      {
        id: 'msg_04',
        senderId: 'usr_rohan_03',
        text: 'Check out this LUT file I sent over.',
        timestamp: '8:30 AM',
        isMe: false
      }
    ]
  }
];
