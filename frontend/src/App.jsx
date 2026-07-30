import { Routes, Route, useLocation } from "react-router-dom";

import LandingPage from "./pages/LandingPage";
import Login from "./pages/Login";
import Welcome from "./pages/Welcome";
import Dashboard from "./pages/Dashboard";
import CompleteProfile from "./pages/CompleteProfile";
import Matches from "./pages/Matches";
import Chat from "./pages/Chat";
import Profile from "./pages/Profile";
import Settings from "./pages/Settings";
import Explore from "./pages/Explore";
import Notifications from "./pages/Notifications";
import Premium from "./pages/Premium";

function App() {
  const location = useLocation();

  // If a background location is stored in state, render the background page
  // behind the modal route (login/signup)
  const backgroundLocation = location.state?.backgroundLocation;

  return (
    <>
      <Routes location={backgroundLocation || location}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<Login isSignup={false} />} />
        <Route path="/signup" element={<Login isSignup={true} />} />
        <Route path="/welcome" element={<Welcome />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/complete-profile" element={<CompleteProfile />} />
        <Route path="/matches" element={<Matches />} />
        <Route path="/chat" element={<Chat />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/settings" element={<Navigate to="/profile" replace />} />
        <Route path="/explore" element={<Explore />} />
        <Route path="/notifications" element={<Notifications />} />
        <Route path="/premium" element={<Premium />} />
      </Routes>

      {/* Modal routes — rendered on top of the background page */}
      {backgroundLocation && (
        <Routes>
          <Route path="/login" element={<Login isSignup={false} isModal />} />
          <Route path="/signup" element={<Login isSignup={true} isModal />} />
        </Routes>
      )}
    </>
  );
}

export default App;