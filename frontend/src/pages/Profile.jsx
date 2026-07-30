import React, { useState, useRef } from 'react';
import { Navigate } from 'react-router-dom';
import { 
  FiEdit2, 
  FiCheck, 
  FiMapPin, 
  FiPlus, 
  FiTrash2, 
  FiCamera,
  FiMusic,
  FiGlobe,
  FiActivity,
  FiCoffee,
  FiBookOpen,
  FiFilm,
  FiTv,
  FiSun,
  FiFeather,
  FiZap,
  FiUser,
  FiLogOut
} from 'react-icons/fi';
import { getStoredAuth, updateProfile, logoutUser } from '../services/auth';
import AppShell from '../components/AppShell';
import './Profile.css';

const INTEREST_ICONS = [
  { label: 'Music', Icon: FiMusic },
  { label: 'Travel', Icon: FiGlobe },
  { label: 'Fitness', Icon: FiActivity },
  { label: 'Art', Icon: FiCamera },
  { label: 'Coffee', Icon: FiCoffee },
  { label: 'Books', Icon: FiBookOpen },
  { label: 'Movies', Icon: FiFilm },
  { label: 'Gaming', Icon: FiTv },
  { label: 'Hiking', Icon: FiSun },
  { label: 'Nature', Icon: FiFeather },
  { label: 'Sports', Icon: FiZap },
];

export default function Profile() {
  const { token, profileData, userName } = getStoredAuth();
  const galleryInputRef = useRef(null);

  const [form, setForm] = useState({
    bio: profileData?.bio || '',
    age: profileData?.age || '',
    location: profileData?.location || '',
    interests: profileData?.interests || [],
    avatar: profileData?.avatar || '',
    gallery: profileData?.gallery || []
  });
  const [saved, setSaved] = useState(false);

  if (!token) return <Navigate to="/login" replace />;

  const toggleInterest = (label) => {
    setForm((prev) => ({
      ...prev,
      interests: prev.interests.includes(label)
        ? prev.interests.filter((i) => i !== label)
        : [...prev.interests, label],
    }));
  };

  const handleAddGalleryPhotos = (e) => {
    const files = Array.from(e.target.files);
    if (!files.length) return;

    files.forEach((file) => {
      const reader = new FileReader();
      reader.onload = (ev) => {
        setForm((prev) => {
          const nextGallery = [...prev.gallery, ev.target.result];
          const nextAvatar = prev.avatar || nextGallery[0];
          return { ...prev, gallery: nextGallery, avatar: nextAvatar };
        });
      };
      reader.readAsDataURL(file);
    });
  };

  const removePhoto = (index) => {
    setForm((prev) => {
      const nextGallery = prev.gallery.filter((_, i) => i !== index);
      const nextAvatar = prev.avatar === prev.gallery[index] ? (nextGallery[0] || '') : prev.avatar;
      return { ...prev, gallery: nextGallery, avatar: nextAvatar };
    });
  };

  const handleSave = (e) => {
    e.preventDefault();
    updateProfile(form);
    setSaved(true);
    setTimeout(() => setSaved(false), 2500);
  };

  const handleLogout = () => {
    logoutUser();
    window.location.href = '/login';
  };

  const displayName = userName || 'Member';
  const mainPhoto = form.avatar || form.gallery[0];

  return (
    <AppShell>
      <div className="profile-page">

        {/* ── Hero Profile Header ── */}
        <div className="profile-hero">
          <div className="profile-avatar-wrap">
            {mainPhoto ? (
              <img src={mainPhoto} alt={displayName} className="profile-avatar-img" />
            ) : (
              <div className="profile-avatar-placeholder">
                <FiUser size={48} color="#FF4F87" />
              </div>
            )}
            <button
              type="button"
              className="profile-avatar-edit"
              onClick={() => galleryInputRef.current?.click()}
              aria-label="Add Photo"
            >
              <FiPlus size={14} />
            </button>
            <input
              type="file"
              accept="image/*"
              multiple
              style={{ display: 'none' }}
              ref={galleryInputRef}
              onChange={handleAddGalleryPhotos}
            />
          </div>

          <h1 className="profile-name">{displayName}{form.age ? `, ${form.age}` : ''}</h1>

          {form.location && (
            <div className="profile-location">
              <FiMapPin size={14} /> {form.location}
            </div>
          )}

          {form.bio && <p className="profile-bio-display">{form.bio}</p>}

          {form.interests.length > 0 && (
            <div className="profile-chips">
              {form.interests.map((tag) => (
                <span key={tag} className="profile-chip">{tag}</span>
              ))}
            </div>
          )}
        </div>

        {/* ── Photo Gallery Section ── */}
        <div className="profile-edit-card">
          <h2 className="profile-section-title"><FiCamera /> Photo Gallery</h2>
          <p className="profile-card-sub">Photos uploaded here will be visible to other members when swiping your profile.</p>

          <div className="profile-gallery-grid">
            {form.gallery.map((photoUrl, idx) => (
              <div
                key={idx}
                className={`profile-gallery-card ${form.avatar === photoUrl ? 'is-main' : ''}`}
                onClick={() => setForm((prev) => ({ ...prev, avatar: photoUrl }))}
              >
                <img src={photoUrl} alt={`Gallery ${idx}`} className="profile-gallery-img" />
                {form.avatar === photoUrl && (
                  <span className="profile-main-tag"><FiUser size={10} /> Main</span>
                )}
                <button
                  type="button"
                  className="profile-photo-delete"
                  onClick={(e) => { e.stopPropagation(); removePhoto(idx); }}
                >
                  <FiTrash2 size={12} />
                </button>
              </div>
            ))}

            <button
              type="button"
              className="profile-add-photo-btn"
              onClick={() => galleryInputRef.current?.click()}
            >
              <FiPlus size={22} />
              <span>Add Photo</span>
            </button>
          </div>
        </div>

        {/* ── Edit Form ── */}
        <form onSubmit={handleSave}>
          <div className="profile-edit-card">
            <h2 className="profile-section-title"><FiEdit2 /> About You</h2>
            <textarea
              className="profile-input profile-textarea"
              placeholder="Write a short bio…"
              value={form.bio}
              onChange={(e) => setForm({ ...form, bio: e.target.value })}
              maxLength={200}
            />
            <div className="profile-row">
              <input
                className="profile-input"
                style={{ marginBottom: 0 }}
                type="number"
                placeholder="Age"
                min={18} max={99}
                value={form.age}
                onChange={(e) => setForm({ ...form, age: e.target.value })}
              />
              <input
                className="profile-input"
                style={{ marginBottom: 0 }}
                type="text"
                placeholder="City / State (e.g. New York, NY)"
                value={form.location}
                onChange={(e) => setForm({ ...form, location: e.target.value })}
              />
            </div>
          </div>

          <div className="profile-edit-card">
            <h2 className="profile-section-title"><FiActivity /> Interests</h2>
            <div className="profile-interest-grid">
              {INTEREST_ICONS.map(({ label, Icon }) => {
                const selected = form.interests.includes(label);
                return (
                  <button
                    key={label}
                    type="button"
                    className={`profile-interest-chip ${selected ? 'selected' : ''}`}
                    onClick={() => toggleInterest(label)}
                  >
                    <Icon size={14} /> {label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="profile-btn-row">
            <button type="submit" className="profile-save-btn">Save Profile & Gallery</button>
            <button type="button" className="profile-logout-btn" onClick={handleLogout}>
              <FiLogOut size={16} /> Log Out
            </button>
          </div>

          {saved && (
            <p className="profile-success">
              <FiCheck size={16} /> Profile updated successfully!
            </p>
          )}
        </form>

      </div>
    </AppShell>
  );
}
