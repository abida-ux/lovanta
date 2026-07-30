// LandingPage.jsx
import React, { useState, useEffect } from 'react';
import { 
  FiHeart, 
  FiShield, 
  FiMessageCircle, 
  FiMenu, 
  FiX, 
  FiArrowRight
} from 'react-icons/fi';
import './LandingPage.css';
import { Link, useLocation } from "react-router-dom";

export default function LandingPage() {
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setMounted(true), 10);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 30);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const closeMenu = () => setMobileMenuOpen(false);

  return (
    <div className="lovanta-scope">
      {/* Sleek Minimal Navbar */}
      <nav className={`navbar ${scrolled ? 'scrolled' : ''}`}>
        <div className="container nav-container">
          <Link to="/" className="logo">
            <FiHeart className="logo-icon" />
            <span>Lovanta</span>
          </Link>

          <div className="nav-actions">
            <Link to="/login" state={{ backgroundLocation: location }} className="login-link">
              Sign In
            </Link>
            <Link to="/signup" state={{ backgroundLocation: location }} className="btn-gradient">
              Create Account
            </Link>
            <button className="menu-toggle" onClick={() => setMobileMenuOpen(true)}>
              <FiMenu />
            </button>
          </div>
        </div>
      </nav>

      {/* Mobile Nav Drawer */}
      {mobileMenuOpen && <div className="mobile-nav-backdrop" onClick={closeMenu} />}
      <div className={`mobile-nav ${mounted && mobileMenuOpen ? 'open' : ''} ${mounted ? 'ready' : ''}`}>
        <div className="mobile-nav-header">
          <Link to="/" className="logo">Lovanta</Link>
          <button className="menu-toggle" onClick={closeMenu}><FiX /></button>
        </div>
        <div className="mobile-actions">
          <Link to="/login" state={{ backgroundLocation: location }} className="login-btn" onClick={closeMenu}>
            Sign In
          </Link>
          <Link to="/signup" state={{ backgroundLocation: location }} className="btn-gradient" onClick={closeMenu}>
            Create Account
          </Link>
        </div>
      </div>

      {/* Compact Hero Header (Half-page layout) */}
      <header className="hero-compact">
        <div className="container hero-compact-content">
          <span className="hero-badge">
            <FiHeart size={14} /> Intentional Connections & Real Matches
          </span>
          <h1 className="hero-compact-title">
            Find Someone Who <span>Complements You</span>
          </h1>
          <p className="hero-compact-desc">
            Lovanta is designed for singles seeking authentic relationships. Skip the endless swiping and connect with people who share your values and lifestyle.
          </p>

          <div className="hero-compact-buttons">
            <Link to="/signup" state={{ backgroundLocation: location }} className="btn-gradient hero-btn">
              Get Started <FiArrowRight size={18} />
            </Link>
            <Link to="/login" state={{ backgroundLocation: location }} className="btn-outline hero-btn">
              Sign In to Your Account
            </Link>
          </div>
        </div>
      </header>

      {/* 3 Core Value Props */}
      <section className="features-compact">
        <div className="container features-grid">
          <div className="feature-card">
            <div className="feature-icon-box">
              <FiHeart />
            </div>
            <h3>Intentional Matching</h3>
            <p>Connect with people based on shared life goals, values, and deep compatibility.</p>
          </div>

          <div className="feature-card">
            <div className="feature-icon-box">
              <FiShield />
            </div>
            <h3>Verified Profiles</h3>
            <p>Every profile is reviewed to ensure authentic interactions and a secure environment.</p>
          </div>

          <div className="feature-card">
            <div className="feature-icon-box">
              <FiMessageCircle />
            </div>
            <h3>Direct Messaging</h3>
            <p>Start meaningful conversations with your matches instantly with real-time chat.</p>
          </div>
        </div>
      </section>

      {/* Minimal Footer */}
      <footer className="footer-compact">
        <div className="container footer-compact-inner">
          <p>© {new Date().getFullYear()} Lovanta. All rights reserved.</p>
          <div className="footer-compact-links">
            <Link to="/login" state={{ backgroundLocation: location }}>Sign In</Link>
            <Link to="/signup" state={{ backgroundLocation: location }}>Create Account</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}