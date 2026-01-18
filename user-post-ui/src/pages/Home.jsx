import { useEffect, useState } from 'react';
import axios from 'axios';

const Home = () => {
  const [posts, setPosts] = useState([]);
  const [content, setContent] = useState('');
  const username = localStorage.getItem('username');
  const userId = localStorage.getItem('userId');

  const fetchPosts = async () => {
    try {
      const res = await axios.get('http://localhost:8080/posts');
      setPosts(res.data);
    } catch (err) { console.error("Error fetching posts:", err); }
  };

  const createPost = async (e) => {
    e.preventDefault();
    if (!content) return;
    
    try {
      await axios.post('http://localhost:8080/posts', { 
        authorId: parseInt(userId), 
        content 
      });
      setContent('');
      fetchPosts();
    } catch (err) {
      console.error("Error creating post:", err);
    }
  };

  useEffect(() => { fetchPosts(); }, []);

  return (
    <div className="min-h-screen bg-gray-50 pt-20 pb-10 px-4">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-3xl font-bold text-gray-800 mb-6">👋 Welcome back, {username}!</h1>

        {/* Input Box */}
        <div className="bg-white p-6 rounded-lg shadow-sm mb-8 border border-gray-200">
          <form onSubmit={createPost} className="flex gap-4">
            <input 
              type="text" 
              value={content} 
              onChange={(e) => setContent(e.target.value)} 
              placeholder="What's happening?" 
              className="flex-1 px-4 py-2 border rounded-full focus:outline-none focus:ring-2 focus:ring-blue-400 bg-gray-50"
            />
            <button type="submit" className="px-6 py-2 bg-blue-500 text-white font-semibold rounded-full hover:bg-blue-600 transition">
              Post
            </button>
          </form>
        </div>

        {/* Feed */}
        <div className="space-y-4">
          {posts.length === 0 ? <p className="text-center text-gray-500">No posts yet. Be the first!</p> : (
            posts.map((post, index) => (
              <div key={index} className="bg-white p-5 rounded-lg shadow-sm border border-gray-100 hover:shadow-md transition">
                <div className="flex items-center mb-2">
                  <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center text-blue-600 font-bold mr-3">
                    {post.authorId}
                  </div>
                  <span className="font-semibold text-gray-700">User #{post.authorId}</span>
                </div>
                <p className="text-gray-800 leading-relaxed">{post.content}</p>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default Home;