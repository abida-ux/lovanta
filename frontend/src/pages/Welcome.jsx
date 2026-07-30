import React from 'react';
import { Link } from 'react-router-dom';
import { FiHeart, FiArrowRight } from 'react-icons/fi';
import './Login.css';

export default function Welcome() {
  return (
    <div className="lovanta-login-wrapper">
      <main className="login-container">
        <section className="form-column">
          <div className="form-content-area">
            <div className="heading-group">
              <h1 className="welcome-title">Welcome to Lovanta</h1>
              <p className="welcome-subtitle">Your account is ready. Let’s help you find the connection you’ve been looking for.</p>
            </div>

            <div className="auth-form">
              <Link to="/" className="btn-submit-gradient" style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', textDecoration: 'none' }}>
                Continue to Home <FiArrowRight style={{ marginLeft: '8px' }} />
              </Link>
            </div>
          </div>
        </section>

        <section className="showcase-column" aria-hidden="true">
          <div className="ambient-blur ab-1"></div>
          <div className="ambient-blur ab-2"></div>
          <div className="showcase-image-wrapper">
            <img
              src="https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?auto=format&fit=crop&w=1200&q=80"
              alt="Lovanta welcome"
              className="showcase-main-img"
            />
          </div>
        </section>
      </main>
    </div>
  );
}
