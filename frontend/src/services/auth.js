import { getStoredUsers } from './api';

export function getStoredAuth() {
  if (typeof window === 'undefined') {
    return { token: null, profileComplete: false, userName: '', profileData: null };
  }

  const token = localStorage.getItem('lovanta_token');
  if (!token) {
    return { token: null, profileComplete: false, userName: '', profileData: null };
  }

  const userId = token.replace('token_', '');
  const userEmail = localStorage.getItem('lovanta_user_email');
  const users = getStoredUsers();

  const currentUser = users.find((u) => 
    u.id === userId || 
    (userEmail && u.email.toLowerCase() === userEmail.toLowerCase())
  );

  if (currentUser) {
    const isComplete = !!(currentUser.profileComplete && currentUser.profileData);
    return {
      token,
      profileComplete: isComplete,
      userName: currentUser.name || localStorage.getItem('lovanta_user_name') || '',
      profileData: currentUser.profileData || null,
    };
  }

  const profileData = localStorage.getItem('lovanta_profile_data');
  return {
    token,
    profileComplete: localStorage.getItem('lovanta_profile_complete') === 'true',
    userName: localStorage.getItem('lovanta_user_name') || '',
    profileData: profileData ? JSON.parse(profileData) : null,
  };
}

export function saveAuth(token, user = {}) {
  if (typeof window === 'undefined') return;

  localStorage.setItem('lovanta_token', token);
  if (user?.name) {
    localStorage.setItem('lovanta_user_name', user.name);
  }
  if (user?.email) {
    localStorage.setItem('lovanta_user_email', user.email);
  }

  const userId = token.replace('token_', '');
  const users = getStoredUsers();
  const idx = users.findIndex((u) => 
    u.id === userId || 
    (user.email && u.email.toLowerCase() === user.email.toLowerCase())
  );

  if (idx !== -1) {
    if (user.profileData) {
      users[idx].profileData = user.profileData;
      users[idx].profileComplete = true;
    }
    const isComplete = !!(users[idx].profileComplete && users[idx].profileData);

    localStorage.setItem('lovanta_real_users', JSON.stringify(users));
    localStorage.setItem('lovanta_profile_complete', isComplete ? 'true' : 'false');
    if (users[idx].profileData) {
      localStorage.setItem('lovanta_profile_data', JSON.stringify(users[idx].profileData));
    }
  } else {
    const isComplete = !!(user.profileComplete && user.profileData);
    localStorage.setItem('lovanta_profile_complete', isComplete ? 'true' : 'false');
    if (user.profileData) {
      localStorage.setItem('lovanta_profile_data', JSON.stringify(user.profileData));
    }
  }
}

export function setProfileComplete(profileData = {}) {
  if (typeof window === 'undefined') return;

  const token = localStorage.getItem('lovanta_token') || '';
  const userId = token.replace('token_', '');
  const userEmail = localStorage.getItem('lovanta_user_email');
  const users = getStoredUsers();
  const idx = users.findIndex((u) => 
    u.id === userId || 
    (userEmail && u.email.toLowerCase() === userEmail.toLowerCase())
  );

  if (idx !== -1) {
    users[idx].profileData = profileData;
    users[idx].profileComplete = true;
    localStorage.setItem('lovanta_real_users', JSON.stringify(users));
  }

  localStorage.setItem('lovanta_profile_complete', 'true');
  localStorage.setItem('lovanta_profile_data', JSON.stringify(profileData));
}

export function updateProfile(profileData = {}) {
  if (typeof window === 'undefined') return;

  const token = localStorage.getItem('lovanta_token') || '';
  const userId = token.replace('token_', '');
  const userEmail = localStorage.getItem('lovanta_user_email');
  const users = getStoredUsers();
  const idx = users.findIndex((u) => 
    u.id === userId || 
    (userEmail && u.email.toLowerCase() === userEmail.toLowerCase())
  );

  let merged = profileData;
  if (idx !== -1) {
    const existing = users[idx].profileData || {};
    merged = { ...existing, ...profileData };
    users[idx].profileData = merged;
    users[idx].profileComplete = true;
    localStorage.setItem('lovanta_real_users', JSON.stringify(users));
  }

  localStorage.setItem('lovanta_profile_complete', 'true');
  localStorage.setItem('lovanta_profile_data', JSON.stringify(merged));
  return merged;
}

export function logoutUser() {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('lovanta_token');
  localStorage.removeItem('lovanta_user_name');
  localStorage.removeItem('lovanta_user_email');
  localStorage.removeItem('lovanta_profile_complete');
  localStorage.removeItem('lovanta_profile_data');
}
