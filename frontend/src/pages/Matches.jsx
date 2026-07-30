import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { FiX, FiHeart, FiStar, FiMoon, FiAward, FiMapPin } from 'react-icons/fi';
import { getStoredAuth } from '../services/auth';
import { fetchCandidates, likeUser } from '../services/api';
import { addNotification } from '../services/notifications';
import AppShell from '../components/AppShell';
import './Matches.css';

export default function Matches() {
  const navigate = useNavigate();
  const { token, profileComplete, profileData, userName } = getStoredAuth();

  const [profiles, setProfiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [matchCelebration, setMatchCelebration] = useState(null);

  const dragState = useRef({ dragging: false, startX: 0, startY: 0, currentX: 0 });
  const cardRef = useRef(null);

  const loadCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchCandidates();
      setProfiles(data || []);
    } catch (err) {
      setProfiles([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (token && profileComplete) {
      loadCandidates();
    }
  }, [token, profileComplete, loadCandidates]);

  if (!token) return <Navigate to="/login" replace />;
  if (!profileComplete) return <Navigate to="/complete-profile" replace />;

  const currentProfile = profiles[0];
  const behindProfile  = profiles[1];
  const behind2Profile = profiles[2];

  const applyDragTransform = (x) => {
    if (!cardRef.current) return;
    const rotate = x * 0.08;
    cardRef.current.style.transform = `translateX(${x}px) rotate(${rotate}deg)`;
    cardRef.current.style.transition = 'none';

    const likeStamp  = cardRef.current.querySelector('.swipe-stamp.like');
    const nopeStamp  = cardRef.current.querySelector('.swipe-stamp.nope');
    if (likeStamp)  likeStamp.style.opacity  = Math.max(0, x / 80);
    if (nopeStamp)  nopeStamp.style.opacity  = Math.max(0, -x / 80);
  };

  const finishSwipe = async (direction) => {
    if (!cardRef.current) return;
    const flyX = direction === 'right' ? 1200 : -1200;
    cardRef.current.style.transition = 'transform 0.35s ease, opacity 0.35s ease';
    cardRef.current.style.transform  = `translateX(${flyX}px) rotate(${direction === 'right' ? 30 : -30}deg)`;
    cardRef.current.style.opacity    = '0';

    const swipedUser = currentProfile;

    setTimeout(async () => {
      if (direction === 'right' && swipedUser) {
        const res = await likeUser(swipedUser.id);
        if (res?.isMatch) {
          setMatchCelebration(swipedUser);
          addNotification({
            type: 'match',
            title: "It's a Match!",
            message: `You and ${swipedUser.name} liked each other!`,
            avatar: swipedUser.photo || swipedUser.avatar,
            link: '/chat'
          });
        }
      }
      setProfiles((prev) => prev.slice(1));
    }, 350);
  };

  const resetCardTransform = () => {
    if (!cardRef.current) return;
    cardRef.current.style.transition = 'transform 0.35s cubic-bezier(0.16, 1, 0.3, 1)';
    cardRef.current.style.transform  = 'translateX(0) rotate(0deg)';
    const likeStamp = cardRef.current.querySelector('.swipe-stamp.like');
    const nopeStamp = cardRef.current.querySelector('.swipe-stamp.nope');
    if (likeStamp) likeStamp.style.opacity = '0';
    if (nopeStamp) nopeStamp.style.opacity = '0';
  };

  const onMouseDown = (e) => {
    dragState.current = { dragging: true, startX: e.clientX, startY: e.clientY, currentX: 0 };
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  };

  const onMouseMove = useCallback((e) => {
    if (!dragState.current.dragging) return;
    const dx = e.clientX - dragState.current.startX;
    dragState.current.currentX = dx;
    applyDragTransform(dx);
  }, []);

  const onMouseUp = useCallback(() => {
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);
    if (!dragState.current.dragging) return;
    dragState.current.dragging = false;
    const dx = dragState.current.currentX;
    if (Math.abs(dx) > 100) {
      finishSwipe(dx > 0 ? 'right' : 'left');
    } else {
      resetCardTransform();
    }
  }, []);

  const onTouchStart = (e) => {
    const touch = e.touches[0];
    dragState.current = { dragging: true, startX: touch.clientX, startY: touch.clientY, currentX: 0 };
  };

  const onTouchMove = (e) => {
    if (!dragState.current.dragging) return;
    const touch = e.touches[0];
    const dx = touch.clientX - dragState.current.startX;
    dragState.current.currentX = dx;
    applyDragTransform(dx);
  };

  const onTouchEnd = () => {
    if (!dragState.current.dragging) return;
    dragState.current.dragging = false;
    const dx = dragState.current.currentX;
    if (Math.abs(dx) > 80) {
      finishSwipe(dx > 0 ? 'right' : 'left');
    } else {
      resetCardTransform();
    }
  };

  const handleSkip = () => finishSwipe('left');
  const handleLike = () => finishSwipe('right');

  const userAvatar = profileData?.avatar || profileData?.gallery?.[0] || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80';

  return (
    <AppShell>
      <div className="matches-page">
        <h1 className="matches-title">Discover</h1>
        <p className="matches-subtitle">Swipe right to like, left to pass</p>

        {loading ? (
          <div className="matches-empty">
            <p>Loading real profiles...</p>
          </div>
        ) : profiles.length === 0 ? (
          <div className="matches-empty">
            <div className="matches-empty-icon-wrap">
              <FiMoon size={48} color="#FF4F87" />
            </div>
            <h3>No more accounts to discover!</h3>
            <p>Register another user account or log in as a second account to start swiping and matching with real profiles.</p>
            <button className="matches-empty-btn" onClick={loadCandidates}>
              Refresh Cards
            </button>
          </div>
        ) : (
          <>
            <div className="swipe-deck">
              {behind2Profile && (
                <div className="swipe-card swipe-card-behind-2">
                  <CardMedia profile={behind2Profile} />
                </div>
              )}
              {behindProfile && (
                <div className="swipe-card swipe-card-behind">
                  <CardMedia profile={behindProfile} />
                </div>
              )}

              <div
                ref={cardRef}
                className="swipe-card swipe-card-front"
                onMouseDown={onMouseDown}
                onTouchStart={onTouchStart}
                onTouchMove={onTouchMove}
                onTouchEnd={onTouchEnd}
              >
                <CardMedia profile={currentProfile} />
                <span className="swipe-stamp like">LIKE</span>
                <span className="swipe-stamp nope">NOPE</span>
              </div>
            </div>

            <div className="swipe-actions">
              <button className="swipe-btn skip" onClick={handleSkip} aria-label="Pass">
                <FiX size={28} />
              </button>
              <button className="swipe-btn super" aria-label="Super Like">
                <FiStar size={22} />
              </button>
              <button className="swipe-btn like" onClick={handleLike} aria-label="Like">
                <FiHeart size={28} />
              </button>
            </div>
          </>
        )}
      </div>

      {matchCelebration && (
        <div className="match-celebration" onClick={() => setMatchCelebration(null)}>
          <div className="match-celebration-sparkle">
            <FiAward size={56} color="#FF4F87" />
          </div>
          <h2>It's a Match!</h2>
          <p>You and {matchCelebration.name} liked each other.</p>

          <div className="match-celebration-avatars">
            <img src={userAvatar} className="match-avatar" alt={userName || "You"} />
            <span className="match-heart-divider"><FiHeart size={24} color="#FF4F87" /></span>
            <img src={matchCelebration.photo || matchCelebration.avatar} className="match-avatar" alt={matchCelebration.name} />
          </div>

          <div className="match-celebration-actions" onClick={(e) => e.stopPropagation()}>
            <button className="match-btn-msg" onClick={() => navigate('/chat')}>
              Send Message
            </button>
            <button className="match-btn-skip" onClick={() => setMatchCelebration(null)}>
              Keep Swiping
            </button>
          </div>
        </div>
      )}
    </AppShell>
  );
}

function CardMedia({ profile }) {
  const [photoIndex, setPhotoIndex] = useState(0);
  const photos = profile.gallery?.length ? profile.gallery : [profile.photo || profile.avatar];

  const handleNextPhoto = (e) => {
    e.stopPropagation();
    setPhotoIndex((prev) => (prev + 1) % photos.length);
  };

  return (
    <>
      <img src={photos[photoIndex] || profile.photo || profile.avatar} alt={profile.name} className="swipe-card-img" draggable={false} />
      
      {photos.length > 1 && (
        <div className="card-photo-indicators" onClick={handleNextPhoto}>
          {photos.map((_, i) => (
            <span key={i} className={`photo-dot ${i === photoIndex ? 'active' : ''}`} />
          ))}
        </div>
      )}

      <div className="swipe-card-overlay">
        <div className="swipe-card-name">{profile.name}{profile.age ? `, ${profile.age}` : ''}</div>
        {profile.location && (
          <div className="swipe-card-sub"><FiMapPin size={13} /> {profile.location}</div>
        )}
        {profile.interests?.length > 0 && (
          <div className="swipe-card-chips">
            {profile.interests.slice(0, 4).map((tag) => (
              <span key={tag} className="swipe-card-chip">{tag}</span>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
