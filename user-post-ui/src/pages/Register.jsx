import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';

const Register = () => {
  const [formData, setFormData] = useState({ username: '', email: '', password: '' });
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await axios.post('http://localhost:8080/register', formData);
      
      alert('Registration Successful! Please Login.');
      navigate('/login');
    } catch (err) {
      // Axios stores the response error in err.response.data
      setError(err.response?.data?.message || 'Registration failed');
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-100">
      <div className="w-full max-w-md p-8 bg-white rounded-lg shadow-md">
        <h2 className="text-2xl font-bold text-center text-gray-800 mb-6">Create Account</h2>
        {error && <div className="mb-4 p-2 bg-red-100 text-red-700 rounded text-center">{error}</div>}
        
        <form onSubmit={handleSubmit} className="space-y-4">
          <input 
            type="text" placeholder="Username" required 
            className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-green-500 outline-none"
            onChange={e => setFormData({...formData, username: e.target.value})} 
          />
          <input 
            type="email" placeholder="Email" required 
            className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-green-500 outline-none"
            onChange={e => setFormData({...formData, email: e.target.value})} 
          />
          <input 
            type="password" placeholder="Password" required 
            className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-green-500 outline-none"
            onChange={e => setFormData({...formData, password: e.target.value})} 
          />
          <button type="submit" className="w-full px-4 py-2 font-bold text-white bg-green-600 rounded-md hover:bg-green-700 transition">
            Sign Up
          </button>
        </form>
        <p className="mt-4 text-sm text-center text-gray-600">
          Already have an account? <Link to="/login" className="text-green-600 hover:underline">Login</Link>
        </p>
      </div>
    </div>
  );
};

export default Register;