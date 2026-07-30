import React, { useState, useEffect } from 'react';
import { Navigate, Link } from 'react-router-dom';
import { FiHeart, FiZap, FiMessageSquare, FiAward, FiBell } from 'react-icons/fi';
import { getStoredAuth } from '../services/auth';
import { getNotifications, markAllAsRead, markAsRead } from '../services/notifications';
import AppShell from '../components/AppShell';
import './Notifications.css';

export default function Notifications() {
  const { token } = getStoredAuth();
  const [list, setList] = useState([]);

  useEffect(() => {
    setList(getNotifications());
  }, []);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const handleMarkAllRead = () => {
    markAllAsRead();
    setList(getNotifications());
  };

  const handleItemClick = (id) => {
    markAsRead(id);
    setList(getNotifications());
  };

  const renderBadgeIcon = (type) => {
    switch (type) {
      case 'match': return <FiHeart size={12} color="#FF4F87" />;
      case 'like': return <FiZap size={12} color="#F97316" />;
      case 'message': return <FiMessageSquare size={12} color="#3B82F6" />;
      default: return <FiAward size={12} color="#8B5CF6" />;
    }
  };

  return (
    <AppShell>
      <div className="notifications-page">
        <div className="notifications-container">
          <div className="notifications-header">
            <div className="notifications-title-box">
              <h1>Activity</h1>
              <p>Likes, matches, and app updates</p>
            </div>
            {list.some(n => !n.read) && (
              <button className="mark-read-btn" onClick={handleMarkAllRead}>
                Mark all as read
              </button>
            )}
          </div>

          {list.length === 0 ? (
            <div className="notifications-empty">
              <div className="notifications-empty-icon">
                <FiBell size={48} color="#FF4F87" />
              </div>
              <h3>No notifications yet</h3>
              <p>When someone likes your profile or sends a message, it will show up here.</p>
            </div>
          ) : (
            <div className="notifications-list">
              {list.map((item) => (
                <Link
                  key={item.id}
                  to={item.link || '/matches'}
                  className={`notification-card ${!item.read ? 'unread' : ''}`}
                  onClick={() => handleItemClick(item.id)}
                >
                  <div className="notification-avatar-box">
                    {item.avatar ? (
                      <img src={item.avatar} alt="" className="notification-avatar" />
                    ) : (
                      <div className="notification-avatar" style={{ background: '#FFF0F5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <FiHeart size={24} color="#FF4F87" />
                      </div>
                    )}
                    <span className="notification-icon-badge">{renderBadgeIcon(item.type)}</span>
                  </div>

                  <div className="notification-content">
                    <div className="notification-title-row">
                      <h3 className="notification-card-title">{item.title}</h3>
                      <span className="notification-time">{item.time}</span>
                    </div>
                    <p className="notification-message">{item.message}</p>
                  </div>

                  {!item.read && <span className="unread-dot" />}
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
