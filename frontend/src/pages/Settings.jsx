import React, { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { getStoredAuth, logoutUser } from '../services/auth';
import AppShell from '../components/AppShell';
import './Settings.css';

export default function Settings() {
  const { token, profileComplete } = getStoredAuth();
  const [isVisible, setIsVisible] = useState(true);
  const [isReadReceipts, setIsReadReceipts] = useState(true);
  const [isNotifications, setIsNotifications] = useState(true);
  const [matchPreference, setMatchPreference] = useState('Friends and dates');
  const [saved, setSaved] = useState(false);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const handleLogout = () => {
    logoutUser();
    window.location.href = '/';
  };

  const handleSave = () => {
    setSaved(true);
    window.setTimeout(() => setSaved(false), 1800);
  };

  return (
    <AppShell>
      <div className="settings-container">
        <div className="settings-card">
          <div className="settings-header">
            <div>
              <h1 className="settings-title">Account Settings</h1>
              <p className="settings-subtitle">Control your privacy, notifications, safety, and how you connect with others.</p>
            </div>
            <div className="settings-pill">Secure and private</div>
          </div>

          <div className="settings-actions">
            <button className="settings-btn primary" onClick={handleSave}>Save changes</button>
            <button className="settings-btn secondary">Edit profile</button>
            <button className="settings-btn danger" onClick={handleLogout}>Log out</button>
          </div>
          {saved && <p className="settings-status">Settings saved successfully</p>}
        </div>

        <div className="settings-grid">
          <div className="settings-section">
            <h3>Privacy</h3>
            <p>Choose who can see your profile and interact with you.</p>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Profile visibility</strong>
                <span>Visible to eligible matches</span>
              </div>
              <button className={`toggle ${isVisible ? 'active' : ''}`} onClick={() => setIsVisible(!isVisible)} />
            </div>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Show online status</strong>
                <span>Let others know when you are active</span>
              </div>
              <button className={`toggle ${isReadReceipts ? 'active' : ''}`} onClick={() => setIsReadReceipts(!isReadReceipts)} />
            </div>
          </div>

          <div className="settings-section">
            <h3>Notifications</h3>
            <p>Stay informed without feeling overwhelmed.</p>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Push notifications</strong>
                <span>Messages, likes, and reminders</span>
              </div>
              <button className={`toggle ${isNotifications ? 'active' : ''}`} onClick={() => setIsNotifications(!isNotifications)} />
            </div>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Read receipts</strong>
                <span>See when your messages are read</span>
              </div>
              <button className={`toggle ${isReadReceipts ? 'active' : ''}`} onClick={() => setIsReadReceipts(!isReadReceipts)} />
            </div>
          </div>

          <div className="settings-section">
            <h3>Matching preferences</h3>
            <p>Adjust how we recommend people to you.</p>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Preferred vibe</strong>
                <span>Choose your ideal match style</span>
              </div>
              <select className="settings-select" value={matchPreference} onChange={(e) => setMatchPreference(e.target.value)}>
                <option>Friends and dates</option>
                <option>Long-term connection</option>
                <option>Casual conversations</option>
                <option>Serious relationship</option>
              </select>
            </div>
          </div>

          <div className="settings-section">
            <h3>Safety</h3>
            <p>Protect your account and report issues quickly.</p>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Block suspicious accounts</strong>
                <span>Review and manage reports</span>
              </div>
              <button className="settings-btn secondary">Manage</button>
            </div>

            <div className="settings-item">
              <div className="settings-item-label">
                <strong>Report a concern</strong>
                <span>Contact support securely</span>
              </div>
              <button className="settings-btn secondary">Report</button>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
