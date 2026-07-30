import React from 'react';
import { Navigate } from 'react-router-dom';
import { getStoredAuth } from '../services/auth';

// Dashboard is just a smart redirector — the real home is the Matches swipe screen
export default function Dashboard() {
  const { token, profileComplete } = getStoredAuth();

  if (!token) return <Navigate to="/login" replace />;
  if (!profileComplete) return <Navigate to="/complete-profile" replace />;

  return <Navigate to="/matches" replace />;
}
