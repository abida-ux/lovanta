import React from 'react';
import { Navigate } from 'react-router-dom';
import { getStoredAuth } from '../services/auth';
import BottomNav from '../components/BottomNav';

export default function Premium() {
  const { token } = getStoredAuth();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div style={{ minHeight: '100vh', background: '#fff8fb', padding: '24px' }}>
      <div style={{ maxWidth: '900px', margin: '0 auto', background: 'white', borderRadius: '24px', padding: '24px', boxShadow: '0 16px 40px rgba(0,0,0,0.08)', marginBottom: '90px' }}>
        <h1 style={{ marginTop: 0 }}>Premium</h1>
        <p style={{ color: '#6b7280' }}>Upgrade for unlimited likes, boosted visibility, and more premium features.</p>
      </div>
      <BottomNav />
    </div>
  );
}
