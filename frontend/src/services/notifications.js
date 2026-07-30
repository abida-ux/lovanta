// src/services/notifications.js

const INITIAL_NOTIFICATIONS = [
  {
    id: 'n1',
    type: 'match',
    title: "It's a Match!",
    message: "You and Sofia liked each other. Say hello!",
    avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80",
    time: "10m ago",
    read: false,
    link: "/chat"
  },
  {
    id: 'n2',
    type: 'like',
    title: "Someone liked you!",
    message: "Elena liked your profile. Swipe to see if it's a match!",
    avatar: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80",
    time: "1h ago",
    read: false,
    link: "/matches"
  },
  {
    id: 'n3',
    type: 'message',
    title: "New Message from Emma",
    message: '"I love that book too! Have you read the sequel?"',
    avatar: "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=200&q=80",
    time: "3h ago",
    read: true,
    link: "/chat"
  },
  {
    id: 'n4',
    type: 'system',
    title: "Welcome to Lovanta 💕",
    message: "Your profile is set up. Start swiping to discover your matches!",
    avatar: null,
    time: "1d ago",
    read: true,
    link: "/matches"
  }
];

export function getNotifications() {
  if (typeof window === 'undefined') return INITIAL_NOTIFICATIONS;
  const stored = localStorage.getItem('lovanta_notifications');
  if (!stored) {
    localStorage.setItem('lovanta_notifications', JSON.stringify(INITIAL_NOTIFICATIONS));
    return INITIAL_NOTIFICATIONS;
  }
  try {
    return JSON.parse(stored);
  } catch (e) {
    return INITIAL_NOTIFICATIONS;
  }
}

export function addNotification(notification) {
  if (typeof window === 'undefined') return;
  const current = getNotifications();
  const newNotif = {
    id: 'n_' + Date.now(),
    read: false,
    time: 'Just now',
    ...notification
  };
  const updated = [newNotif, ...current];
  localStorage.setItem('lovanta_notifications', JSON.stringify(updated));
  // Dispatch custom event for real-time header badge updates
  window.dispatchEvent(new Event('lovanta_notifications_updated'));
}

export function getUnreadCount() {
  const all = getNotifications();
  return all.filter(n => !n.read).length;
}

export function markAllAsRead() {
  if (typeof window === 'undefined') return;
  const current = getNotifications();
  const updated = current.map(n => ({ ...n, read: true }));
  localStorage.setItem('lovanta_notifications', JSON.stringify(updated));
  window.dispatchEvent(new Event('lovanta_notifications_updated'));
}

export function markAsRead(id) {
  if (typeof window === 'undefined') return;
  const current = getNotifications();
  const updated = current.map(n => n.id === id ? { ...n, read: true } : n);
  localStorage.setItem('lovanta_notifications', JSON.stringify(updated));
  window.dispatchEvent(new Event('lovanta_notifications_updated'));
}
