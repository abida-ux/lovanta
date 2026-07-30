import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { FiHeart, FiBell, FiSettings, FiLogOut } from 'react-icons/fi';
import BottomNav from './BottomNav';
import { getUnreadCount } from '../services/notifications';
import { logoutUser } from '../services/auth';
import './AppShell.css';

export default function AppShell({ children }) {
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    const updateCount = () => {
      setUnreadCount(getUnreadCount());
    };

    updateCount();
    window.addEventListener('lovanta_notifications_updated', updateCount);
    return () => window.removeEventListener('lovanta_notifications_updated', updateCount);
  }, []);

  const handleLogout = () => {
    logoutUser();
    window.location.href = '/login';
  };

  return (
    <div className="app-shell">
      {/* Top bar */}
      <header className="app-topbar">
        <Link to="/matches" className="app-topbar-logo">
          <FiHeart size={20} />
          Lovanta
        </Link>

        <div className="app-topbar-actions">
          <Link to="/notifications" className="app-topbar-icon-btn" aria-label="Notifications">
            <FiBell size={20} />
            {unreadCount > 0 && <span className="app-topbar-badge" />}
          </Link>
          <button className="app-topbar-icon-btn" onClick={handleLogout} aria-label="Log Out" title="Log Out">
            <FiLogOut size={19} color="#EF4444" />
          </button>
        </div>
      </header>

      {/* Page content */}
      <main className="app-page-content">
        {children}
      </main>

      <BottomNav />
    </div>
  );
}
