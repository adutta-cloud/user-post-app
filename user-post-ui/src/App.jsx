import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useState } from 'react';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';

function App() {
  // Check if token exists to decide if user is logged in
  const [isAuthenticated] = useState(!!localStorage.getItem('token'));

  const handleLogout = () => {
    localStorage.clear();
    window.location.href = '/login';
  };

  return (
    <Router>
      {/* Navbar */}
      <nav className="fixed top-0 left-0 right-0 bg-white shadow-sm border-b border-gray-200 z-50 h-16 flex items-center justify-between px-6">
        <div className="text-xl font-bold text-blue-600 tracking-tight">
          GridSocial
        </div>
        {isAuthenticated && (
          <button 
            onClick={handleLogout} 
            className="px-4 py-2 text-sm font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition"
          >
            Logout
          </button>
        )}
      </nav>

      {/* Routes */}
      <Routes>
        <Route path="/login" element={!isAuthenticated ? <Login /> : <Navigate to="/" />} />
        <Route path="/register" element={!isAuthenticated ? <Register /> : <Navigate to="/" />} />
        
        {/* Protected Route: Home */}
        <Route path="/" element={isAuthenticated ? <Home /> : <Navigate to="/login" />} />
      </Routes>
    </Router>
  );
}

export default App;