import React, { useState, useRef } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { 
  FiHeart, 
  FiUpload, 
  FiCheck, 
  FiArrowRight, 
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
  FiUser
} from 'react-icons/fi';
import { getStoredAuth, setProfileComplete } from '../services/auth';
import './CompleteProfile.css';

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

const STEP_LABELS = ['Photos & Gallery', 'About You', 'Interests'];

export default function CompleteProfile() {
  const navigate = useNavigate();
  const { token, profileComplete, userName } = getStoredAuth();
  const galleryInputRef = useRef(null);

  const [step, setStep] = useState(0);
  const [error, setError] = useState('');

  // Step 1 — Photos & Gallery (Starts empty for real uploaded photos)
  const [gallery, setGallery] = useState([]);
  const [selectedAvatar, setSelectedAvatar] = useState('');

  // Step 2 — About
  const [bio, setBio] = useState('');
  const [age, setAge] = useState('');
  const [location, setLocation] = useState('');

  // Step 3 — Interests
  const [interests, setInterests] = useState([]);

  if (!token) return <Navigate to="/login" replace />;
  if (profileComplete) return <Navigate to="/matches" replace />;

  const handleGalleryUpload = (e) => {
    const files = Array.from(e.target.files);
    if (!files.length) return;

    files.forEach((file) => {
      const reader = new FileReader();
      reader.onload = (ev) => {
        setGallery((prev) => {
          if (prev.length >= 6) return prev;
          const next = [...prev, ev.target.result];
          if (!selectedAvatar) setSelectedAvatar(next[0]);
          return next;
        });
      };
      reader.readAsDataURL(file);
    });
  };

  const removeGalleryPhoto = (index) => {
    setGallery((prev) => {
      const next = prev.filter((_, i) => i !== index);
      if (selectedAvatar === prev[index]) {
        setSelectedAvatar(next[0] || '');
      }
      return next;
    });
  };

  const toggleInterest = (label) => {
    setInterests((prev) =>
      prev.includes(label) ? prev.filter((i) => i !== label) : [...prev, label]
    );
  };

  const validateStep = () => {
    if (step === 0 && gallery.length === 0) {
      setError('Please upload at least 1 photo to your gallery.');
      return false;
    }
    if (step === 1) {
      if (!bio.trim()) { setError('Please write a short bio.'); return false; }
      if (!age || isNaN(age) || +age < 18 || +age > 99) { setError('Please enter a valid age (18–99).'); return false; }
      if (!location.trim()) { setError('Please enter your location.'); return false; }
    }
    if (step === 2 && interests.length < 3) {
      setError('Pick at least 3 interests so we can find your best matches!');
      return false;
    }
    return true;
  };

  const handleNext = () => {
    setError('');
    if (!validateStep()) return;
    if (step < 2) {
      setStep(step + 1);
    } else {
      const avatar = selectedAvatar || gallery[0] || '';
      setProfileComplete({
        name: userName || 'New User',
        avatar,
        gallery,
        bio,
        age,
        location,
        interests
      });
      navigate('/matches', { replace: true });
    }
  };

  const handleBack = () => {
    setError('');
    setStep(step - 1);
  };

  const renderStepDots = () => (
    <div className="cp-steps-row">
      {STEP_LABELS.map((label, i) => (
        <React.Fragment key={i}>
          <div className={`cp-step-dot ${i < step ? 'done' : i === step ? 'current' : ''}`}>
            {i < step ? <FiCheck size={14} /> : i + 1}
          </div>
          {i < STEP_LABELS.length - 1 && (
            <div className={`cp-step-line ${i < step ? 'filled' : ''}`} />
          )}
        </React.Fragment>
      ))}
    </div>
  );

  return (
    <div className="cp-wrapper">
      <div className="cp-header">
        <FiHeart size={22} />
        Lovanta
      </div>

      <div className="cp-progress">
        {renderStepDots()}
        <p className="cp-progress-label">
          Step <strong>{step + 1}</strong> of {STEP_LABELS.length} — <strong>{STEP_LABELS[step]}</strong>
        </p>
      </div>

      <div className="cp-card" key={step}>
        {step === 0 && (
          <>
            <h2 className="cp-card-title">Upload your gallery</h2>
            <p className="cp-card-subtitle">Upload your photos below. Other members will see these when viewing your profile!</p>

            <div className="cp-gallery-grid">
              {gallery.map((photoUrl, idx) => (
                <div
                  key={idx}
                  className={`cp-gallery-item ${selectedAvatar === photoUrl ? 'is-main' : ''}`}
                  onClick={() => setSelectedAvatar(photoUrl)}
                >
                  <img src={photoUrl} alt={`Gallery ${idx + 1}`} className="cp-gallery-img" />
                  {selectedAvatar === photoUrl && (
                    <span className="main-badge"><FiUser size={10} /> Main</span>
                  )}
                  <button
                    type="button"
                    className="cp-remove-photo-btn"
                    onClick={(e) => { e.stopPropagation(); removeGalleryPhoto(idx); }}
                  >
                    <FiTrash2 size={12} />
                  </button>
                </div>
              ))}

              {gallery.length < 6 && (
                <button
                  type="button"
                  className="cp-add-gallery-btn"
                  onClick={() => galleryInputRef.current?.click()}
                >
                  <FiPlus size={24} />
                  <span>Add Photo</span>
                </button>
              )}
            </div>

            <input
              type="file"
              accept="image/*"
              multiple
              className="cp-upload-input"
              ref={galleryInputRef}
              onChange={handleGalleryUpload}
            />
          </>
        )}

        {step === 1 && (
          <>
            <h2 className="cp-card-title">About you</h2>
            <p className="cp-card-subtitle">A great bio helps us find your best matches.</p>

            <label className="cp-label">Your bio</label>
            <textarea
              className="cp-input cp-textarea"
              placeholder="Write a little about yourself, what you love, and what you're looking for…"
              value={bio}
              maxLength={200}
              onChange={(e) => setBio(e.target.value)}
            />

            <div className="cp-row">
              <div>
                <label className="cp-label">Age</label>
                <input
                  className="cp-input"
                  type="number"
                  placeholder="25"
                  min={18}
                  max={99}
                  value={age}
                  onChange={(e) => setAge(e.target.value)}
                  style={{ marginBottom: 0 }}
                />
              </div>
              <div>
                <label className="cp-label">City / State</label>
                <input
                  className="cp-input"
                  type="text"
                  placeholder="New York, NY"
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  style={{ marginBottom: 0 }}
                />
              </div>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h2 className="cp-card-title">Your interests</h2>
            <p className="cp-card-subtitle">Pick at least 3 things you love — we'll use these to find your ideal matches.</p>
            <p className="cp-chip-hint">{interests.length} selected{interests.length < 3 ? ` (${3 - interests.length} more to go)` : ' ✓'}</p>
            
            <div className="cp-chips">
              {INTEREST_ICONS.map(({ label, Icon }) => {
                const selected = interests.includes(label);
                return (
                  <button
                    key={label}
                    type="button"
                    className={`cp-chip ${selected ? 'selected' : ''}`}
                    onClick={() => toggleInterest(label)}
                  >
                    <Icon size={16} /> {label}
                  </button>
                );
              })}
            </div>
          </>
        )}

        {error && <p className="cp-error">{error}</p>}

        <div className="cp-actions">
          {step > 0 && (
            <button className="cp-btn-back" onClick={handleBack}>← Back</button>
          )}
          <button className="cp-btn-next" onClick={handleNext}>
            {step === 2 ? 'Finish Setup' : <>Continue <FiArrowRight size={16} /></>}
          </button>
        </div>
      </div>
    </div>
  );
}
