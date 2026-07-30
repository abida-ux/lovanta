// src/pages/Chat.jsx
import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Navigate } from 'react-router-dom';
import { FiSend, FiInfo, FiChevronLeft, FiX, FiHeart, FiMapPin, FiMessageSquare } from 'react-icons/fi';
import { getStoredAuth } from '../services/auth';
import { fetchMatches, fetchMessages, sendMessage } from '../services/api';
import AppShell from '../components/AppShell';
import './Chat.css';

export default function Chat() {
  const { token, userName } = getStoredAuth();
  const [matches, setMatches] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [typedText, setTypedText] = useState('');
  const [showDrawer, setShowDrawer] = useState(false);
  const [mobileView, setMobileView] = useState('list');
  const [messages, setMessages] = useState([]);
  const [loadingMatches, setLoadingMatches] = useState(true);
  const [isTyping, setIsTyping] = useState(false);
  const streamEndRef = useRef(null);

  const loadMatchesData = useCallback(async () => {
    setLoadingMatches(true);
    try {
      const data = await fetchMatches();
      setMatches(data || []);
      if (data?.length && !activeId) {
        setActiveId(data[0].id);
      }
    } catch (err) {
      setMatches([]);
    } finally {
      setLoadingMatches(false);
    }
  }, [activeId]);

  useEffect(() => {
    if (token) {
      loadMatchesData();
    }
  }, [token, loadMatchesData]);

  const loadMessagesData = useCallback(async (recipientId) => {
    if (!recipientId) return;
    try {
      const msgs = await fetchMessages(recipientId);
      setMessages(msgs || []);
    } catch (err) {
      setMessages([]);
    }
  }, []);

  useEffect(() => {
    if (activeId) {
      loadMessagesData(activeId);
    }
  }, [activeId, loadMessagesData]);

  useEffect(() => {
    streamEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  if (!token) return <Navigate to="/login" replace />;

  const activeUser = matches.find(c => c.id === activeId) || matches[0];

  const handleSelectChat = (id) => {
    setActiveId(id);
    setMobileView('chat');
    setShowDrawer(false);
    setIsTyping(false);
    loadMessagesData(id);
  };

  const handleSend = async (e) => {
    e?.preventDefault();
    if (!typedText.trim() || !activeId) return;

    const textToSend = typedText.trim();
    setTypedText('');

    const newMsg = await sendMessage(activeId, textToSend);
    setMessages(prev => [...prev, newMsg || {
      id: 'msg_' + Date.now(),
      sender: 'me',
      text: textToSend,
      createdAt: new Date().toISOString()
    }]);

    // Simulate real-time response & typing indicator from matched user
    setTimeout(() => {
      setIsTyping(true);
    }, 800);

    setTimeout(async () => {
      setIsTyping(false);
      const responses = [
        "Hey! Great hearing from you 😊 How is your day going?",
        "Loved your photos! What are your favorite spots around town?",
        "That sounds awesome! We should definitely get coffee sometime soon.",
        "Haha totally agree! Always nice connecting with someone who shares similar vibes."
      ];
      const randomReply = responses[Math.floor(Math.random() * responses.length)];
      
      // Save simulated reply into local storage / message list
      const chatKey = `lovanta_chat_${['token_' + activeId, token].sort().join('_')}`;
      const replyMsg = {
        id: 'msg_' + Date.now(),
        sender: activeId,
        recipient: 'me',
        text: randomReply,
        createdAt: new Date().toISOString()
      };
      
      setMessages(prev => [...prev, replyMsg]);
    }, 2800);
  };

  const handleQuickHeart = () => {
    setTypedText('❤️');
  };

  const currentUserId = token ? token.replace('token_', '') : '';

  return (
    <AppShell>
      <div className="tinder-chat-container">
        <div className="tinder-chat-card">

          {/* ── LEFT SIDEBAR ── */}
          <aside className={`tinder-sidebar ${mobileView === 'chat' ? 'mobile-hidden' : ''}`}>
            <div className="tinder-sidebar-header">
              <h2 className="tinder-sidebar-title">Matches & Messages</h2>
            </div>

            {loadingMatches ? (
              <div className="convos-section" style={{ padding: '20px', textAlign: 'center', color: '#888' }}>
                <p>Loading your real matches...</p>
              </div>
            ) : matches.length === 0 ? (
              <div className="convos-section" style={{ padding: '30px 20px', textAlign: 'center', color: '#888' }}>
                <FiHeart size={32} color="#FF4F87" style={{ marginBottom: '8px' }} />
                <h4 style={{ color: '#222', fontSize: '15px', marginBottom: '4px' }}>No matches yet</h4>
                <p style={{ fontSize: '12px' }}>Start swiping in the Discover tab to match and unlock messaging with real users!</p>
              </div>
            ) : (
              <>
                {/* Matches Row */}
                <div className="new-matches-section">
                  <span className="new-matches-label">Matched Users</span>
                  <div className="new-matches-scroll">
                    {matches.map(item => (
                      <div key={item.id} className="new-match-item" onClick={() => handleSelectChat(item.id)}>
                        <div className="new-match-avatar-ring">
                          <img src={item.avatar || item.photo} alt={item.name} className="new-match-img" />
                        </div>
                        <span className="new-match-name">{item.name}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Messages List */}
                <div className="convos-section">
                  <span className="convos-section-label">Conversations</span>
                  {matches.map(item => (
                    <div
                      key={item.id}
                      className={`convo-item ${item.id === activeId ? 'active' : ''}`}
                      onClick={() => handleSelectChat(item.id)}
                    >
                      <div className="convo-avatar-box">
                        <img src={item.avatar || item.photo} alt={item.name} className="convo-avatar" />
                        <span className="convo-online-dot" />
                      </div>

                      <div className="convo-info">
                        <div className="convo-top-row">
                          <h4 className="convo-name">{item.name}</h4>
                        </div>
                        <p className="convo-snippet">{item.bio || 'Matched user'}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </aside>

          {/* ── CHAT VIEWPORT ── */}
          <section className={`tinder-viewport ${mobileView === 'list' ? 'mobile-hidden' : ''}`}>
            
            {activeUser ? (
              <>
                {/* Header */}
                <header className="tinder-viewport-header">
                  <div className="tinder-user-header">
                    <button className="mobile-back-btn" onClick={() => setMobileView('list')}>
                      <FiChevronLeft />
                    </button>
                    <img src={activeUser.avatar || activeUser.photo} alt={activeUser.name} className="header-avatar" />
                    <div className="header-name-box">
                      <h3>{activeUser.name}{activeUser.age ? `, ${activeUser.age}` : ''}</h3>
                      <span className={`header-status ${activeUser.online ? 'online' : 'offline'}`}>
                        <span className="status-dot" /> {activeUser.online ? 'Online now' : 'Active recently'}
                      </span>
                    </div>
                  </div>

                  <button
                    className="info-btn"
                    onClick={() => setShowDrawer(!showDrawer)}
                    title="View Profile Info"
                  >
                    <FiInfo />
                  </button>
                </header>

                {/* Stream */}
                <div className="tinder-messages-stream">
                  <div className="match-notice-banner">
                    <p>❤️ You matched with {activeUser.name}! Send a real message below.</p>
                  </div>

                  {messages.map(msg => {
                    const isMe = msg.sender === 'me' || msg.sender === currentUserId || (userName && msg.sender === userName);
                    return (
                      <div key={msg.id} className={`msg-bubble-wrap ${isMe ? 'me' : 'them'}`}>
                        <div className="msg-bubble">{msg.text}</div>
                        <span className="msg-meta">
                          {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Just now'}
                        </span>
                      </div>
                    );
                  })}

                  {/* Typing Dots Bubble */}
                  {isTyping && (
                    <div className="msg-bubble-wrap them">
                      <div className="typing-bubble">
                        <span className="typing-dot" />
                        <span className="typing-dot" />
                        <span className="typing-dot" />
                      </div>
                      <span className="msg-meta">{activeUser.name} is typing...</span>
                    </div>
                  )}

                  <div ref={streamEndRef} />
                </div>

                {/* Input dock */}
                <form onSubmit={handleSend} className="tinder-input-dock">
                  <button type="button" className="quick-heart-btn" onClick={handleQuickHeart} title="Send Heart">
                    <FiHeart color="#FF4F87" size={20} />
                  </button>
                  <input
                    type="text"
                    className="tinder-chat-input"
                    placeholder={`Type a message to ${activeUser.name}...`}
                    value={typedText}
                    onChange={e => setTypedText(e.target.value)}
                  />
                  <button type="submit" className="tinder-send-btn" aria-label="Send">
                    <FiSend />
                  </button>
                </form>

                {/* Profile Drawer */}
                {showDrawer && (
                  <div className="profile-drawer">
                    <button className="drawer-close-btn" onClick={() => setShowDrawer(false)}>
                      <FiX />
                    </button>
                    <img src={activeUser.avatar || activeUser.photo} alt={activeUser.name} className="drawer-img" />
                    <h3 className="drawer-name">{activeUser.name}{activeUser.age ? `, ${activeUser.age}` : ''}</h3>
                    {activeUser.location && <p className="drawer-location"><FiMapPin size={12} /> {activeUser.location}</p>}

                    {activeUser.bio && <p className="drawer-bio">"{activeUser.bio}"</p>}

                    {activeUser.interests?.length > 0 && (
                      <div className="drawer-tags">
                        {activeUser.interests.map(t => (
                          <span key={t} className="drawer-tag">{t}</span>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </>
            ) : (
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: '#888' }}>
                <FiMessageSquare size={48} color="#FF4F87" style={{ marginBottom: '12px' }} />
                <h3>No active conversation</h3>
                <p style={{ fontSize: '13px' }}>Select a match from the left menu to start messaging.</p>
              </div>
            )}

          </section>

        </div>
      </div>
    </AppShell>
  );
}