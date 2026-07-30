// src/pages/Login.jsx
import React, { useEffect, useState } from 'react';
import { 
  FiHeart, 
  FiEye, 
  FiEyeOff, 
  FiMail, 
  FiLock, 
  FiCheck,
  FiX,
  FiUserCheck
} from 'react-icons/fi';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import './Login.css';
import { loginUser, registerUser } from '../services/api';
import { saveAuth, getStoredAuth } from '../services/auth';

export default function Login({ isSignup = false, isModal = false }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [formData, setFormData] = useState({ name: '', email: '', password: '' });
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('info');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [visible, setVisible] = useState(false);

  const { token, profileComplete } = getStoredAuth();

  useEffect(() => {
    const t = setTimeout(() => setVisible(true), 10);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    if (token) {
      navigate(profileComplete ? '/matches' : '/complete-profile', { replace: true });
    }
  }, [token, profileComplete, navigate]);

  useEffect(() => {
    if (isModal) {
      document.body.style.overflow = 'hidden';
      return () => { document.body.style.overflow = ''; };
    }
  }, [isModal]);

  if (token) return null;

  const handleClose = () => {
    setVisible(false);
    setTimeout(() => navigate(-1), 300);
  };

  const togglePasswordVisibility = (e) => {
    e.preventDefault();
    setShowPassword(!showPassword);
  };

  const showMessage = (text, type = 'info') => {
    setMessage(text);
    setMessageType(type);
  };

  const executeLogin = async (email, password, name = '') => {
    showMessage('');
    setIsSubmitting(true);

    try {
      if (isSignup) {
        const result = await registerUser({ name: name || formData.name, email, password });
        saveAuth(result.token, { name: name || formData.name, profileComplete: false });
        showMessage('Account created! Redirecting...', 'success');
        window.setTimeout(() => navigate('/complete-profile'), 600);
      } else {
        const result = await loginUser({ email, password });
        const isComplete = !!(result.user?.profileComplete && result.user?.profileData);
        saveAuth(result.token, {
          id: result.user?.id,
          email: result.user?.email || email,
          name: result.user?.name || email,
          profileData: result.user?.profileData,
          profileComplete: isComplete
        });
        showMessage('Signed in! Redirecting...', 'success');
        window.setTimeout(() => {
          navigate(isComplete ? '/matches' : '/complete-profile');
        }, 600);
      }
    } catch (error) {
      showMessage(error.message || 'Something went wrong. Please try again.', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleFormSubmit = (e) => {
    e.preventDefault();
    executeLogin(formData.email, formData.password, formData.name);
  };

  const headingTitle  = isSignup ? 'Create Your Account' : 'Welcome Back';
  const headingSubtitle = isSignup ? 'Start your journey with Lovanta.' : 'Where Meaningful Connections Begin.';
  const submitLabel   = isSignup ? 'Create Free Account' : 'Sign In to Lovanta';
  const footerPrompt  = isSignup ? 'Already have an account?' : "Don't have an account?";
  const footerAction  = isSignup ? 'Sign In' : 'Create Account';
  const footerLink    = isSignup ? '/login' : '/signup';

  const card = (
    <div className={`login-card ${visible ? 'login-card--visible' : ''}`}>

      {isModal && (
        <button className="modal-close-btn" onClick={handleClose} aria-label="Close">
          <FiX />
        </button>
      )}

      <header className="brand-header">
        <Link to="/" className="brand-logo" aria-label="Lovanta Home">
          <FiHeart className="brand-icon" />
          <span className="brand-name">Lovanta</span>
        </Link>
      </header>

      <div className="heading-group">
        <h1 className="welcome-title">{headingTitle}</h1>
        <p className="welcome-subtitle">{headingSubtitle}</p>
      </div>

      <form onSubmit={handleFormSubmit} className="auth-form">
        {isSignup && (
          <div className="input-group">
            <label htmlFor="name" className="input-label">Full Name</label>
            <div className="input-wrapper">
              <FiHeart className="field-icon" />
              <input
                type="text"
                id="name"
                className="form-input"
                placeholder="Alex Morgan"
                required
                autoComplete="name"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              />
            </div>
          </div>
        )}

        <div className="input-group">
          <label htmlFor="email" className="input-label">Email Address</label>
          <div className="input-wrapper">
            <FiMail className="field-icon" />
            <input
              type="email"
              id="email"
              className="form-input"
              placeholder="name@example.com"
              required
              autoComplete="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            />
          </div>
        </div>

        <div className="input-group">
          <label htmlFor="password" className="input-label">Password</label>
          <div className="input-wrapper">
            <FiLock className="field-icon" />
            <input
              type={showPassword ? 'text' : 'password'}
              id="password"
              className="form-input"
              placeholder="••••••••••••"
              required
              autoComplete="current-password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            />
            <button
              type="button"
              onClick={togglePasswordVisibility}
              className="password-toggle-btn"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <FiEyeOff /> : <FiEye />}
            </button>
          </div>
        </div>

        <div className="form-options">
          <label className="checkbox-container">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={() => setRememberMe(!rememberMe)}
              className="hidden-checkbox"
            />
            <span className={`custom-checkbox ${rememberMe ? 'checked' : ''}`}>
              {rememberMe && <FiCheck className="check-mark" />}
            </span>
            <span className="checkbox-label">Remember me</span>
          </label>
          <a href="#" className="forgot-password-link">Forgot Password?</a>
        </div>

        {message && <p className={`auth-message ${messageType}`}>{message}</p>}

        <button type="submit" className="btn-submit-gradient" disabled={isSubmitting}>
          {isSubmitting ? 'Please wait...' : submitLabel}
        </button>
      </form>

      <div className="divider-container">
        <span className="divider-line"></span>
        <span className="divider-text">or continue with</span>
        <span className="divider-line"></span>
      </div>

      <button type="button" className="btn-social google" onClick={() => executeLogin("alex@lovanta.com", "password123", "Alex Morgan")}>
        <svg className="social-svg-icon" viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
          <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
          <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" fill="#FBBC05"/>
          <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" fill="#EA4335"/>
        </svg>
        <span>Continue with Google</span>
      </button>

      <footer className="form-footer">
        <p className="footer-switch-text">
          {footerPrompt}{' '}
          <Link
            to={footerLink}
            state={isModal ? { backgroundLocation: location.state?.backgroundLocation } : undefined}
            className="footer-action-link"
          >
            {footerAction}
          </Link>
        </p>
      </footer>
    </div>
  );

  if (isModal) {
    return (
      <div className={`auth-modal-overlay ${visible ? 'auth-modal-overlay--visible' : ''}`} onClick={handleClose}>
        <div onClick={(e) => e.stopPropagation()}>
          {card}
        </div>
      </div>
    );
  }

  return (
    <div className="lovanta-login-wrapper">
      {card}
    </div>
  );
}