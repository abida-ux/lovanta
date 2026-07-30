import React from 'react';
import { NavLink } from 'react-router-dom';
import { FiHeart, FiMessageCircle, FiUser } from 'react-icons/fi';
import './BottomNav.css';

export default function BottomNav() {
  return (
    <nav className="bottom-nav">
      <NavLink
        to="/chat"
        className={({ isActive }) => `bottom-nav-link ${isActive ? 'active' : ''}`}
      >
        <FiMessageCircle size={22} />
        <span>Chat</span>
      </NavLink>

      <NavLink
        to="/matches"
        className={({ isActive }) => `bottom-nav-link match-tab ${isActive ? 'active' : ''}`}
      >
        <FiHeart size={24} />
        <span>Match</span>
      </NavLink>

      <NavLink
        to="/profile"
        className={({ isActive }) => `bottom-nav-link ${isActive ? 'active' : ''}`}
      >
        <FiUser size={22} />
        <span>Profile</span>
      </NavLink>
    </nav>
  );
}
