// src/pages/Login.jsx
import React, { useState } from 'react';
import { 
  FiHeart, 
  FiEye, 
  FiEyeOff, 
  FiMail, 
  FiLock, 
  FiCheck, 
  FiShield, 
  FiUsers, 
  FiAward 
} from 'react-icons/fi';
import './Login.css';

export default function Login() {
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);

  const togglePasswordVisibility = (e) => {
    e.preventDefault();
    setShowPassword(!showPassword);
  };

  const handleFormSubmit = (e) => {
    e.preventDefault();
  };

  return (
    <div className="lovanta-login-wrapper">
      <main className="login-container">
        
        {/* Left Column: Form Section */}
        <section className="form-column">
          <header className="brand-header">
            <a href="#" className="brand-logo" aria-label="Lovanta Home">
              <FiHeart className="brand-icon" />
              <span className="brand-name">Lovanta</span>
            </a>
          </header>

          <div className="form-content-area">
            <div className="heading-group">
              <h1 className="welcome-title">Welcome Back</h1>
              <p className="welcome-subtitle">Where Meaningful Connections Begin.</p>
            </div>

            <form onSubmit={handleFormSubmit} className="auth-form">
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
                  />
                </div>
              </div>

              <div className="input-group">
                <label htmlFor="password" className="input-label">Password</label>
                <div className="input-wrapper">
                  <FiLock className="field-icon" />
                  <input 
                    type={showPassword ? "text" : "password"} 
                    id="password" 
                    className="form-input" 
                    placeholder="••••••••••••" 
                    required 
                    autoComplete="current-password"
                  />
                  <button 
                    type="button"
                    onClick={togglePasswordVisibility}
                    className="password-toggle-btn"
                    aria-label={showPassword ? "Hide password" : "Show password"}
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

              <button type="submit" className="btn-submit-gradient">
                Sign In to Lovanta
              </button>
            </form>

            <div className="divider-container">
              <span className="divider-line"></span>
              <span className="divider-text">or continue with</span>
              <span className="divider-line"></span>
            </div>

            <div className="social-button-group">
              <button type="button" className="btn-social google">
                <svg className="social-svg-icon" viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg">
                  <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                  <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                  <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" fill="#FBBC05"/>
                  <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" fill="#EA4335"/>
                </svg>
                <span>Google</span>
              </button>
              <button type="button" className="btn-social facebook">
                <svg className="social-svg-icon" viewBox="0 0 24 24" width="18" height="18" fill="#1877F2" xmlns="http://www.w3.org/2000/svg">
                  <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                </svg>
                <span>Facebook</span>
              </button>
            </div>

            <footer className="form-footer">
              <p className="footer-switch-text">
                Don't have an account? <a href="#" className="footer-action-link">Create Account</a>
              </p>
            </footer>
          </div>
        </section>

        {/* Right Column: Imagery & Premium Cards Section */}
        <section className="showcase-column" aria-hidden="true">
          <div className="ambient-blur ab-1"></div>
          <div className="ambient-blur ab-2"></div>
          
          <div className="showcase-image-wrapper">
            <img 
              src="https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=1200&q=80" 
              alt="Romantic premium couple connection" 
              className="showcase-main-img"
            />
          </div>

          {/* Floating Luxury Glass Cards */}
          <div className="glass-card gc-verification animation-float-delayed">
            <div className="glass-card-icon-container circle-green">
              <FiShield className="g-icon" />
            </div>
            <div className="glass-card-text">
              <h4 className="g-title">Verified Profiles</h4>
              <p className="g-subtitle">100% Secure & Authentic</p>
            </div>
          </div>

          <div className="glass-card gc-members animation-float">
            <div className="glass-card-icon-container circle-pink">
              <FiUsers className="g-icon" />
            </div>
            <div className="glass-card-text">
              <h4 className="g-title">10K+ Members</h4>
              <p className="g-subtitle">Active Premium Singles</p>
            </div>
          </div>

          <div className="glass-card gc-satisfaction animation-float-delayed">
            <div className="glass-card-icon-container circle-gold">
              <FiAward className="g-icon" />
            </div>
            <div className="glass-card-text">
              <h4 className="g-title">95% Match Satisfaction</h4>
              <p className="g-subtitle">Highly Accurate Frameworks</p>
            </div>
          </div>
        </section>

      </main>
    </div>
  );
}