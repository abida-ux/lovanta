// LandingPage.jsx
import React, { useState, useEffect } from 'react';
import { 
  FiHeart, 
  FiUserCheck, 
  FiMessageSquare, 
  FiShield, 
  FiUsers, 
  FiTrendingUp, 
  FiChevronDown, 
  FiPlay, 
  FiInstagram, 
  FiTwitter, 
  FiFacebook, 
  FiMenu, 
  FiX, 
  FiLayers,
  FiAward
} from 'react-icons/fi';
import './LandingPage.css';
import { Link } from "react-router-dom";

export default function LandingPage() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [activeFaq, setActiveFaq] = useState(null);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 50);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className="lovanta-scope">
      {/* Premium Sticky Navbar */}
      <nav className={`navbar ${scrolled ? 'scrolled' : ''}`}>
        <div className="container nav-container">
          <a href="#" className="logo">
            <FiHeart className="logo-icon" />
            Lovanta
          </a>
          
          <ul className="nav-links">
            <li><a href="#">Home</a></li>
            <li><a href="#how-it-works">How It Works</a></li>
            <li><a href="#safety">Safety</a></li>
            <li><a href="#stories">Success Stories</a></li>
            <li><a href="#about">About</a></li>
          </ul>

          <div className="nav-actions">
            <Link to="/login" className="login-link">Login</Link>
            <button className="btn-gradient">Start Matching</button>
            <button className="menu-toggle" onClick={() => setMobileMenuOpen(true)}>
              <FiMenu />
            </button>
          </div>
        </div>
      </nav>

      {/* Mobile Navigation Overlay */}
      <div className={`mobile-nav ${mobileMenuOpen ? 'open' : ''}`}>
        <div className="mobile-nav-header">
          <a href="#" className="logo">Lovanta</a>
          <button className="menu-toggle" onClick={() => setMobileMenuOpen(false)}>
            <FiX />
          </button>
        </div>
        <ul className="mobile-links">
          <li><a href="#" onClick={() => setMobileMenuOpen(false)}>Home</a></li>
          <li><a href="#how-it-works" onClick={() => setMobileMenuOpen(false)}>How It Works</a></li>
          <li><a href="#safety" onClick={() => setMobileMenuOpen(false)}>Safety</a></li>
          <li><a href="#stories" onClick={() => setMobileMenuOpen(false)}>Success Stories</a></li>
          <li><a href="#about" onClick={() => setMobileMenuOpen(false)}>About</a></li>
        </ul>
        <div className="mobile-actions">
          <Link to="/login" className="login-btn">Login</Link>
          <button className="btn-gradient">Start Matching</button>
        </div>
      </div>

      {/* Hero Section */}
      <header className="hero">
        <div className="container hero-grid">
          <div className="hero-content">
            <h1 className="hero-headline">
              Find Meaningful<br />
              <span>Connections</span><br />
              That Last.
            </h1>
            <p className="hero-p">
              Lovanta carefully matches compatible individuals based on deep personality alignment, core life values, and real connection goals. Say goodbye to superficial swiping.
            </p>
            <div className="hero-actions">
              <button className="btn-gradient">Start Your Journey</button>
              <button className="btn-outline">
                <FiPlay /> Watch How It Works
              </button>
            </div>
          </div>

          <div className="hero-media">
            <div className="circle-gradient cg-1"></div>
            <div className="hero-img-wrapper">
              <img 
                src="https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?auto=format&fit=crop&w=800&q=80" 
                alt="Genuine happy couple laughing outside" 
                className="hero-img"
              />
            </div>

            {/* Floating Glass UI Cards */}
            <div className="floating-card fc-1">
              <img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80" alt="Sarah" className="avatar" />
              <div>
                <h4>Sarah, 28 <span className="indicator"></span></h4>
                <p>Matched perfectly!</p>
              </div>
            </div>

            <div className="floating-card fc-2">
              <div className="heart-icon-box">
                <FiHeart className="heart-filled" />
              </div>
              <div>
                <h4>New Match Recommended</h4>
                <p>95% Compatibility Score</p>
              </div>
            </div>

            <div className="floating-card fc-3">
              <img src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80" alt="David" className="avatar" />
              <div>
                <h4>David <span className="heart-text">♥</span></h4>
                <p>Online Now</p>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Trusted Statistics */}
      <section className="stats">
        <div className="container">
          <div className="stats-grid">
            <div className="stat-card">
              <FiUsers className="stat-icon" />
              <div className="stat-number">10K+</div>
              <div className="stat-desc">Members</div>
            </div>
            <div className="stat-card">
              <FiAward className="stat-icon" />
              <div className="stat-number">95%</div>
              <div className="stat-desc">Successful Matches</div>
            </div>
            <div className="stat-card">
              <FiMessageSquare className="stat-icon" />
              <div className="stat-number">24/7</div>
              <div className="stat-desc">Support</div>
            </div>
            <div className="stat-card">
              <FiShield className="stat-icon" />
              <div className="stat-number">100%</div>
              <div className="stat-desc">Private Conversations</div>
            </div>
          </div>
        </div>
      </section>

      {/* How Lovanta Works */}
      <section id="how-it-works" className="how-it-works">
        <div className="container">
          <h2 className="section-title">How Lovanta Works</h2>
          <p className="section-subtitle">An intentional, curated approach designed to establish long-term relationships without the burnout.</p>
          
          <div className="how-grid">
            <div className="how-card">
              <span className="step-badge">Step 1</span>
              <div className="how-icon-box"><FiUserCheck /></div>
              <h3>Create Account</h3>
              <p>Securely connect your profile and complete our foundational verification checks.</p>
            </div>

            <div className="how-card">
              <span className="step-badge">Step 2</span>
              <div className="how-icon-box"><FiLayers /></div>
              <h3>Complete Your Profile</h3>
              <p>Highlight your core personality values, lifestyles, and relationship parameters.</p>
            </div>

            <div className="how-card">
              <span className="step-badge">Step 3</span>
              <div className="how-icon-box"><FiHeart /></div>
              <h3>Receive Curated Matches</h3>
              <p>Get highly accurate, handpicked recommendations daily calculated by deep alignment metrics.</p>
            </div>

            <div className="how-card">
              <span className="step-badge">Step 4</span>
              <div className="how-icon-box"><FiMessageSquare /></div>
              <h3>Start Conversations</h3>
              <p>Engage in fluid, continuous communication streams inside premium, secure interaction spaces.</p>
            </div>
          </div>
        </div>
      </section>

      {/* Why Lovanta Feature Grid */}
      <section id="safety" className="why-lovanta">
        <div className="container">
          <h2 className="section-title">Why Lovanta</h2>
          <p className="section-subtitle">We build premium infrastructure optimized entirely around user safety, emotional depth, and authenticity.</p>
          
          <div className="why-grid">
            <div className="why-card">
              <FiAward className="why-icon" />
              <h3>Smart Matching</h3>
              <p>Advanced mapping models emphasizing psychological values over surface data points.</p>
            </div>
            <div className="why-card">
              <FiUserCheck className="why-icon" />
              <h3>Verified Members</h3>
              <p>Strict structural verification methods to guarantee you communicate with real individuals.</p>
            </div>
            <div className="why-card">
              <FiShield className="why-icon" />
              <h3>Private Messaging</h3>
              <p>Full encryption systems shielding personal communication channels from extraneous exposure.</p>
            </div>
            <div className="why-card">
              <FiHeart className="why-icon" />
              <h3>Real Relationships</h3>
              <p>A community designed and managed explicitly for singles searching for commitment.</p>
            </div>
            <div className="why-card">
              <FiX className="why-icon" />
              <h3>No Endless Swiping</h3>
              <p>Controlled, limited daily matching queues engineered specifically to protect your mental focus.</p>
            </div>
            <div className="why-card">
              <FiTrendingUp className="why-icon" />
              <h3>Compatibility Based</h3>
              <p>Every account is evaluated across comprehensive alignment metrics before pairing options compile.</p>
            </div>
            <div className="why-card">
              <FiShield className="why-icon" />
              <h3>Safe Community</h3>
              <p>Zero-tolerance behavioral guardrails provide a safe environment for all members.</p>
            </div>
            <div className="why-card">
              <FiMessageSquare className="why-icon" />
              <h3>Modern Chat Experience</h3>
              <p>Fluid, feature-rich interface layers supporting elegant interaction flow.</p>
            </div>
          </div>
        </div>
      </section>

      {/* Featured Success Stories */}
      <section id="stories" className="success-stories">
        <div className="container">
          <h2 className="section-title">Featured Success Stories</h2>
          <p className="section-subtitle">Real experiences shared by amazing couples who discovered their lifelong companion on Lovanta.</p>
          
          <div className="stories-grid">
            <div className="story-card">
              <div>
                <div className="stars">★★★★★</div>
                <p className="story-text">"Lovanta transformed how we approached online dating. The match accuracy was so precise that our very first conversation felt completely fluid and natural, like reconnecting with an old friend."</p>
              </div>
              <div className="story-author">
                <img src="https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=150&q=80" alt="Michael and Sophia" className="author-img" />
                <div>
                  <h4 className="author-name">Michael & Sophia</h4>
                  <p className="author-status">Married 1 Year</p>
                </div>
              </div>
            </div>

            <div className="story-card">
              <div>
                <div className="stars">★★★★★</div>
                <p className="story-text">"The thorough verification and focus on value compatibility attracted exactly who I was searching for. I met Marcus within two weeks, and we've been inseparable ever since."</p>
              </div>
              <div className="story-author">
                <img src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80" alt="Elena and Marcus" className="author-img" />
                <div>
                  <h4 className="author-name">Elena & Marcus</h4>
                  <p className="author-status">Engaged</p>
                </div>
              </div>
            </div>

            <div className="story-card">
              <div>
                <div className="stars">★★★★★</div>
                <p className="story-text">"The pacing here is wonderful. Getting qualitative matching recommendations instead of an infinite list allowed us to focus deeply. We are aligned across all our life goals."</p>
              </div>
              <div className="story-author">
                <img src="https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=150&q=80" alt="Liam and Clara" className="author-img" />
                <div>
                  <h4 className="author-name">Liam & Clara</h4>
                  <p className="author-status">Together 2 Years</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* FAQ Accordion Section */}
      <section id="about" className="faq">
        <div className="container">
          <h2 className="section-title">Frequently Asked Questions</h2>
          <p className="section-subtitle">Clear responses to popular questions concerning platform functionality and core safety.</p>
          
          <div className="faq-wrapper">
            {[
              {
                q: "Is Lovanta free?",
                a: "Lovanta provides fundamental profile configuration along with a basic matching tier for free. Advanced communication access requires an upgraded premium subscription choice."
              },
              {
                q: "How do matching recommendations work?",
                a: "Our system evaluates core personality values, long-term expectations, and communication models to yield authentic, highly compatible match pairings."
              },
              {
                q: "Can I search for users?",
                a: "To preserve user privacy and prevent algorithmic catalog fatigue, Lovanta relies exclusively on programmatic matching instead of standard public profile browsing."
              },
              {
                q: "Is my information private?",
                a: "Yes. Your personal information is secured with multi-layered data encryption protocols and fully isolated profile environments."
              }
            ].map((item, idx) => (
              <div key={idx} className={`faq-item ${activeFaq === idx ? 'active' : ''}`}>
                <button className="faq-trigger" onClick={() => setActiveFaq(activeFaq === idx ? null : idx)}>
                  <span>{item.q}</span>
                  <FiChevronDown className="faq-icon" />
                </button>
                <div className="faq-content">
                  <p>{item.a}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Final Call to Action */}
      <section className="final-cta">
        <div className="cta-bg-shape cta-shape-1"></div>
        <div className="cta-bg-shape cta-shape-2"></div>
        <div className="container final-cta-container">
          <h2>Your Next Conversation Could Change Your Life.</h2>
          <p>Join a curated, intentional network of wonderful singles searching for deep connection today.</p>
          <button className="btn-gradient large-btn">Create Free Account</button>
        </div>
      </section>

      {/* Premium Footer */}
      <footer className="footer">
        <div className="container">
          <div className="footer-grid">
            <div className="footer-brand">
              <a href="#" className="logo">Lovanta</a>
              <p>Where Meaningful Connections Begin. A production platform built exclusively for exceptional relationships.</p>
            </div>
            
            <div className="footer-col">
              <h4>Quick Links</h4>
              <ul className="footer-links">
                <li><a href="#how-it-works">How It Works</a></li>
                <li><a href="#stories">Success Stories</a></li>
                <li><a href="#about">About</a></li>
              </ul>
            </div>

            <div className="footer-col">
              <h4>Safety & Policies</h4>
              <ul className="footer-links">
                <li><a href="#">Privacy Policy</a></li>
                <li><a href="#">Terms of Service</a></li>
                <li><a href="#safety">Safety Guidelines</a></li>
              </ul>
            </div>

            <div className="footer-col">
              <h4>Connect</h4>
              <p className="support-email">support@lovanta.com</p>
              <div className="social-box">
                <a href="#" className="social-icon"><FiInstagram /></a>
                <a href="#" className="social-icon"><FiTwitter /></a>
                <a href="#" className="social-icon"><FiFacebook /></a>
              </div>
            </div>
          </div>

          <div className="footer-bottom">
            <div>&copy; {new Date().getFullYear()} Lovanta Global Inc. All rights reserved.</div>
            <div className="footer-legal-links">
              <a href="#">Privacy</a>
              <a href="#">Terms</a>
              <a href="#">Cookies</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}