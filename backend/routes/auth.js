const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const User = require('../models/User');

const router = express.Router();

// Helper to seed initial test accounts in MongoDB
async function seedInitialUsersIfNeeded() {
  try {
    const abed = await User.findOne({ email: 'abedanyakundi1@gmail.com' });
    let abedId, sophiaId;

    if (!abed) {
      const hashedAbed = await bcrypt.hash('Lan123tan', 10);
      const createdAbed = await User.create({
        name: 'Abed Nyakundi',
        email: 'abedanyakundi1@gmail.com',
        password: hashedAbed,
        profileComplete: true,
        age: 26,
        location: 'New York, NY',
        bio: 'Software engineer & tech enthusiast. Love hiking in upstate NY, photography, and great espresso.',
        interests: ['Art', 'Coffee', 'Hiking', 'Music'],
        avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=600&q=80',
        gallery: [
          'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=600&q=80',
          'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=600&q=80'
        ]
      });
      abedId = createdAbed._id;
    } else {
      abedId = abed._id;
    }

    const sophia = await User.findOne({ email: 'sophia.martinez@gmail.com' });
    if (!sophia) {
      const hashedSophia = await bcrypt.hash('Lan123tan', 10);
      const createdSophia = await User.create({
        name: 'Sophia Martinez',
        email: 'sophia.martinez@gmail.com',
        password: hashedSophia,
        profileComplete: true,
        age: 25,
        location: 'Los Angeles, CA',
        bio: 'Architect & violinist based in LA. Love exploring hidden galleries, beach sunsets, and indie music.',
        interests: ['Art', 'Music', 'Photography', 'Travel'],
        avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=600&q=80',
        gallery: [
          'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=600&q=80',
          'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80'
        ]
      });
      sophiaId = createdSophia._id;
    } else {
      sophiaId = sophia._id;
    }

    // Ensure Abed and Sophia are matched in MongoDB
    await User.findByIdAndUpdate(abedId, { $addToSet: { matches: sophiaId, likes: sophiaId } });
    await User.findByIdAndUpdate(sophiaId, { $addToSet: { matches: abedId, likes: abedId } });
  } catch (err) {
    console.error('Seed users error:', err);
  }
}

router.post('/register', async (req, res) => {
  const { name, email, password } = req.body;

  if (!name || !email || !password) {
    return res.status(400).json({ message: 'All fields are required' });
  }

  try {
    const existingUser = await User.findOne({ email: email.toLowerCase() });
    if (existingUser) {
      return res.status(409).json({ message: 'Email already registered' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const user = await User.create({ name, email: email.toLowerCase(), password: hashedPassword });

    const token = jwt.sign(
      { id: user._id },
      process.env.JWT_SECRET || 'dev-secret',
      { expiresIn: '30d' }
    );

    res.status(201).json({
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        profileComplete: user.profileComplete || false,
      },
    });
  } catch (error) {
    res.status(500).json({ message: 'Registration failed', error: error.message });
  }
});

router.post('/login', async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ message: 'Email and password are required' });
  }

  try {
    await seedInitialUsersIfNeeded();

    const user = await User.findOne({ email: email.toLowerCase() });
    if (!user) {
      return res.status(401).json({ message: 'Invalid credentials' });
    }

    const isValidPassword = await bcrypt.compare(password, user.password);
    if (!isValidPassword) {
      return res.status(401).json({ message: 'Invalid credentials' });
    }

    const token = jwt.sign(
      { id: user._id },
      process.env.JWT_SECRET || 'dev-secret',
      { expiresIn: '30d' }
    );

    res.json({
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        profileComplete: user.profileComplete || false,
        profileData: {
          bio: user.bio || '',
          age: user.age || '',
          location: user.location || '',
          interests: user.interests || [],
          avatar: user.avatar || '',
          gallery: user.gallery || [],
        },
      },
    });
  } catch (error) {
    res.status(500).json({ message: 'Login failed', error: error.message });
  }
});

module.exports = router;
