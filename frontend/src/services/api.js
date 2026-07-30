const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:5000/api';

// Seeded real accounts with USA locations
const INITIAL_USERS = [
  {
    id: 'usr_abed',
    name: 'Abed Nyakundi',
    email: 'abedanyakundi1@gmail.com',
    password: 'Lan123tan',
    profileComplete: true,
    profileData: {
      bio: 'Software engineer & tech enthusiast. Love hiking in upstate NY, photography, and great espresso.',
      age: 26,
      location: 'New York, NY',
      interests: ['Art', 'Coffee', 'Hiking', 'Music'],
      avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=600&q=80',
      gallery: [
        'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=600&q=80',
        'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=600&q=80'
      ]
    }
  },
  {
    id: 'usr_sophia',
    name: 'Sophia Martinez',
    email: 'sophia.martinez@gmail.com',
    password: 'Lan123tan',
    profileComplete: true,
    profileData: {
      bio: 'Architect & violinist based in LA. Love exploring hidden galleries, beach sunsets, and indie music.',
      age: 25,
      location: 'Los Angeles, CA',
      interests: ['Art', 'Music', 'Photography', 'Travel'],
      avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=600&q=80',
      gallery: [
        'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=600&q=80',
        'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80'
      ]
    }
  }
];

export function getStoredUsers() {
  if (typeof window === 'undefined') return INITIAL_USERS;
  const stored = localStorage.getItem('lovanta_real_users');
  if (!stored) {
    localStorage.setItem('lovanta_real_users', JSON.stringify(INITIAL_USERS));
    return INITIAL_USERS;
  }
  try {
    return JSON.parse(stored);
  } catch (e) {
    return INITIAL_USERS;
  }
}

function getAuthHeaders() {
  const token = localStorage.getItem('lovanta_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  const text = await response.text();
  return text ? { message: text } : {};
}

// ── Auth APIs ──
export async function registerUser(payload) {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await parseResponse(response);
    if (response.ok) return data;
    throw new Error(data.message || 'Registration failed');
  } catch (err) {
    if (err.message && err.message !== 'Failed to fetch' && !err.message.includes('fetch')) {
      throw err;
    }
  }

  const users = getStoredUsers();
  const existing = users.find((u) => u.email.toLowerCase() === payload.email.toLowerCase());
  if (existing) {
    throw new Error('Email already registered');
  }

  const newUser = {
    id: 'usr_' + Date.now(),
    name: payload.name,
    email: payload.email.toLowerCase(),
    password: payload.password,
    profileComplete: false,
    profileData: null
  };

  users.push(newUser);
  localStorage.setItem('lovanta_real_users', JSON.stringify(users));

  return {
    token: 'token_' + newUser.id,
    user: { id: newUser.id, name: newUser.name, email: newUser.email, profileComplete: false }
  };
}

export async function loginUser(payload) {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await parseResponse(response);
    if (response.ok) return data;
    throw new Error(data.message || 'Invalid email or password');
  } catch (err) {
    if (err.message && err.message !== 'Failed to fetch' && !err.message.includes('fetch')) {
      throw err;
    }
  }

  const users = getStoredUsers();
  const user = users.find(
    (u) => u.email.toLowerCase() === payload.email.toLowerCase() && u.password === payload.password
  );

  if (!user) {
    throw new Error('Invalid email or password');
  }

  return {
    token: 'token_' + user.id,
    user: {
      id: user.id,
      name: user.name,
      email: user.email,
      profileComplete: !!(user.profileComplete && user.profileData),
      profileData: user.profileData || null,
    },
  };
}

// ── User Profile APIs ──
export async function updateUserProfile(profileData) {
  try {
    const response = await fetch(`${API_BASE_URL}/users/profile`, {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: JSON.stringify(profileData),
    });
    const data = await parseResponse(response);
    if (response.ok) return data;
    throw new Error(data.message || 'Failed to update profile');
  } catch (err) {
    if (err.message && err.message !== 'Failed to fetch' && !err.message.includes('fetch')) {
      throw err;
    }
  }

  const token = localStorage.getItem('lovanta_token') || '';
  const userId = token.replace('token_', '');
  const users = getStoredUsers();
  const idx = users.findIndex((u) => u.id === userId);

  if (idx !== -1) {
    users[idx].profileData = { ...(users[idx].profileData || {}), ...profileData };
    users[idx].profileComplete = true;
    localStorage.setItem('lovanta_real_users', JSON.stringify(users));
  }

  return { message: 'Profile updated' };
}

export async function deleteAccount() {
  const token = localStorage.getItem('lovanta_token') || '';
  const currentUserId = token.replace('token_', '');
  const currentUserEmail = localStorage.getItem('lovanta_user_email') || '';

  try {
    await fetch(`${API_BASE_URL}/users/profile`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
  } catch (err) {
    // Offline fallback
  }

  const stored = localStorage.getItem('lovanta_real_users');
  if (stored) {
    try {
      const users = JSON.parse(stored).filter(
        (u) => u.id !== currentUserId && u.email?.toLowerCase() !== currentUserEmail?.toLowerCase()
      );
      localStorage.setItem('lovanta_real_users', JSON.stringify(users));
    } catch (e) {
      // Ignore
    }
  }

  return { message: 'Account permanently deleted' };
}

export async function fetchCandidates() {
  try {
    const response = await fetch(`${API_BASE_URL}/users/candidates`, {
      headers: getAuthHeaders(),
    });
    const data = await parseResponse(response);
    if (response.ok && Array.isArray(data)) return data;
  } catch (err) {
    // Dev fallback
  }

  const token = localStorage.getItem('lovanta_token') || '';
  const currentUserId = token.replace('token_', '');
  const currentUserEmail = localStorage.getItem('lovanta_user_email') || '';
  const matchesKey = `lovanta_matches_${currentUserId}`;
  const swipedIds = JSON.parse(localStorage.getItem(matchesKey) || '[]');
  const users = getStoredUsers();

  return users
    .filter((u) => 
      u.id !== currentUserId && 
      u.email.toLowerCase() !== currentUserEmail.toLowerCase() && 
      !swipedIds.includes(u.id) &&
      u.profileData && 
      u.profileComplete
    )
    .map((u) => ({
      id: u.id,
      name: u.name,
      age: u.profileData.age || 25,
      location: u.profileData.location || 'New York, NY',
      bio: u.profileData.bio || '',
      interests: u.profileData.interests || [],
      photo: u.profileData.avatar || u.profileData.gallery?.[0] || '',
      gallery: u.profileData.gallery || [u.profileData.avatar || ''],
    }));
}

export async function likeUser(targetId) {
  try {
    const response = await fetch(`${API_BASE_URL}/users/like/${targetId}`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    const data = await parseResponse(response);
    if (response.ok) return data;
  } catch (err) {
    // Dev fallback
  }

  const token = localStorage.getItem('lovanta_token') || '';
  const currentUserId = token.replace('token_', '');
  const matchesKey = `lovanta_matches_${currentUserId}`;
  const matches = JSON.parse(localStorage.getItem(matchesKey) || '[]');

  if (!matches.includes(targetId)) {
    matches.push(targetId);
    localStorage.setItem(matchesKey, JSON.stringify(matches));
  }

  const targetMatchesKey = `lovanta_matches_${targetId}`;
  const targetMatches = JSON.parse(localStorage.getItem(targetMatchesKey) || '[]');

  if (!targetMatches.includes(currentUserId)) {
    targetMatches.push(currentUserId);
    localStorage.setItem(targetMatchesKey, JSON.stringify(targetMatches));
  }

  return { isMatch: true };
}

export async function fetchMatches() {
  try {
    const response = await fetch(`${API_BASE_URL}/users/matches`, {
      headers: getAuthHeaders(),
    });
    const data = await parseResponse(response);
    if (response.ok && Array.isArray(data)) return data;
  } catch (err) {
    // Dev fallback
  }

  const token = localStorage.getItem('lovanta_token') || '';
  const currentUserId = token.replace('token_', '');
  const matchesKey = `lovanta_matches_${currentUserId}`;
  let matchIds = JSON.parse(localStorage.getItem(matchesKey) || 'null');

  if (!matchIds) {
    if (currentUserId === 'usr_abed') matchIds = ['usr_sophia'];
    else if (currentUserId === 'usr_sophia') matchIds = ['usr_abed'];
    else matchIds = [];
    localStorage.setItem(matchesKey, JSON.stringify(matchIds));
  }
  const users = getStoredUsers();

  return users
    .filter((u) => matchIds.includes(u.id))
    .map((u) => ({
      id: u.id,
      name: u.name,
      age: u.profileData?.age || 25,
      avatar: u.profileData?.avatar || u.profileData?.gallery?.[0] || '',
      location: u.profileData?.location || 'New York, NY',
      bio: u.profileData?.bio || '',
      interests: u.profileData?.interests || [],
    }));
}

// ── Messages API ──
export async function fetchMessages(recipientId) {
  try {
    const response = await fetch(`${API_BASE_URL}/chat/${recipientId}`, {
      headers: getAuthHeaders(),
    });
    const data = await parseResponse(response);
    if (response.ok && Array.isArray(data)) return data;
  } catch (err) {
    // Dev fallback
  }

  const token = localStorage.getItem('lovanta_token') || '';
  const currentUserId = token.replace('token_', '');
  const chatKey = `lovanta_chat_${[currentUserId, recipientId].sort().join('_')}`;
  return JSON.parse(localStorage.getItem(chatKey) || '[]');
}

export async function sendMessage(recipientId, text) {
  try {
    const response = await fetch(`${API_BASE_URL}/chat/send`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ recipientId, text }),
    });
    const data = await parseResponse(response);
    if (response.ok) return data;
  } catch (err) {
    // Dev fallback
  }

  const token = localStorage.getItem('lovanta_token') || '';
  const currentUserId = token.replace('token_', '');
  const chatKey = `lovanta_chat_${[currentUserId, recipientId].sort().join('_')}`;
  const currentMsgs = JSON.parse(localStorage.getItem(chatKey) || '[]');

  const newMsg = {
    id: 'msg_' + Date.now(),
    sender: currentUserId,
    recipient: recipientId,
    text,
    createdAt: new Date().toISOString(),
  };

  currentMsgs.push(newMsg);
  localStorage.setItem(chatKey, JSON.stringify(currentMsgs));
  return newMsg;
}
