# 💕 Lovanta — Dating App

A full-stack dating app with a React/Vite frontend and a Node.js/Express backend.

## 📁 Project Structure

```
lovanta/
├── frontend/          # React + Vite app
│   ├── public/        # Static assets (favicon, images)
│   ├── src/
│   │   ├── assets/    # Images and SVGs
│   │   ├── components/# Reusable components (BottomNav, etc.)
│   │   ├── pages/     # Page components (LandingPage, Chat, Login...)
│   │   ├── services/  # API & auth service helpers
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── backend/           # Node.js + Express API
│   ├── config/        # Database config (db.js)
│   ├── models/        # Mongoose models (User.js)
│   ├── routes/        # Express routes (auth.js)
│   ├── server.js      # Entry point
│   └── package.json
│
├── package.json       # Root orchestrator (runs both together)
└── .gitignore
```

## 🚀 Getting Started

### Install all dependencies

```bash
npm run install:all
```

### Run both frontend and backend together

```bash
npm run dev
```

### Run individually

```bash
npm run dev:frontend   # React app  → http://localhost:5173
npm run dev:backend    # Express API → http://localhost:5000
```

### Build for production

```bash
npm run build
```

## ⚙️ Environment Variables

Copy `backend/.env.example` to `backend/.env` and fill in your values:

```bash
cp backend/.env.example backend/.env
```

## 🛠️ Tech Stack

| Layer    | Technology                          |
|----------|-------------------------------------|
| Frontend | React 19, Vite, React Router, Axios |
| Backend  | Node.js, Express, MongoDB, Socket.io|
| Auth     | JWT, bcrypt                         |
| Realtime | Socket.io                           |
