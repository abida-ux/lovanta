const express = require('express');
const jwt = require('jsonwebtoken');
const User = require('../models/User');

const router = express.Router();

// Middleware to authenticate JWT
const authenticate = (req, res, next) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: 'Unauthorized' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'dev-secret');
    req.userId = decoded.id;
    next();
  } catch (err) {
    return res.status(401).json({ message: 'Invalid token' });
  }
};

// GET current user profile
router.get('/me', authenticate, async (req, res) => {
  try {
    const user = await User.findById(req.userId).select('-password');
    if (!user) return res.status(404).json({ message: 'User not found' });
    res.json(user);
  } catch (err) {
    res.status(500).json({ message: 'Failed to fetch user profile' });
  }
});

// UPDATE profile (Complete onboarding / Edit profile)
router.put('/profile', authenticate, async (req, res) => {
  const { bio, age, location, interests, avatar, gallery } = req.body;

  try {
    const user = await User.findById(req.userId);
    if (!user) return res.status(404).json({ message: 'User not found' });

    if (bio !== undefined) user.bio = bio;
    if (age !== undefined) user.age = age;
    if (location !== undefined) user.location = location;
    if (interests !== undefined) user.interests = interests;
    if (avatar !== undefined) user.avatar = avatar;
    if (gallery !== undefined) user.gallery = gallery;
    user.profileComplete = true;

    await user.save();

    res.json({
      message: 'Profile updated successfully',
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        profileComplete: user.profileComplete,
        bio: user.bio,
        age: user.age,
        location: user.location,
        interests: user.interests,
        avatar: user.avatar,
        gallery: user.gallery,
      },
    });
  } catch (err) {
    res.status(500).json({ message: 'Failed to update profile' });
  }
});

// GET candidate profiles for swiping
router.get('/candidates', authenticate, async (req, res) => {
  try {
    const currentUser = await User.findById(req.userId);
    if (!currentUser) return res.status(404).json({ message: 'User not found' });

    const excludedIds = [
      currentUser._id,
      ...currentUser.likes,
      ...currentUser.dislikes,
    ];

    const candidates = await User.find({
      _id: { $nin: excludedIds },
      profileComplete: true,
    })
      .select('-password')
      .limit(20);

    res.json(candidates);
  } catch (err) {
    res.status(500).json({ message: 'Failed to fetch candidates' });
  }
});

// LIKE a user
router.post('/like/:targetId', authenticate, async (req, res) => {
  const { targetId } = req.params;

  try {
    const currentUser = await User.findById(req.userId);
    const targetUser = await User.findById(targetId);

    if (!currentUser || !targetUser) {
      return res.status(404).json({ message: 'User not found' });
    }

    if (!currentUser.likes.includes(targetId)) {
      currentUser.likes.push(targetId);
    }

    // Check if targetUser has already liked currentUser
    const isMutual = targetUser.likes.includes(currentUser._id);

    if (isMutual) {
      if (!currentUser.matches.includes(targetId)) currentUser.matches.push(targetId);
      if (!targetUser.matches.includes(currentUser._id)) targetUser.matches.push(currentUser._id);
      await targetUser.save();
    }

    await currentUser.save();

    res.json({
      message: isMutual ? "It's a Match!" : 'Like recorded',
      isMatch: isMutual,
      targetUser: {
        id: targetUser._id,
        name: targetUser.name,
        avatar: targetUser.avatar || targetUser.gallery?.[0],
      },
    });
  } catch (err) {
    res.status(500).json({ message: 'Failed to process like' });
  }
});

// DISLIKE a user
router.post('/dislike/:targetId', authenticate, async (req, res) => {
  const { targetId } = req.params;

  try {
    const currentUser = await User.findById(req.userId);
    if (!currentUser) return res.status(404).json({ message: 'User not found' });

    if (!currentUser.dislikes.includes(targetId)) {
      currentUser.dislikes.push(targetId);
      await currentUser.save();
    }

    res.json({ message: 'Dislike recorded' });
  } catch (err) {
    res.status(500).json({ message: 'Failed to record dislike' });
  }
});

// GET all user matches
router.get('/matches', authenticate, async (req, res) => {
  try {
    const currentUser = await User.findById(req.userId).populate(
      'matches',
      '-password'
    );
    if (!currentUser) return res.status(404).json({ message: 'User not found' });

    res.json(currentUser.matches);
  } catch (err) {
    res.status(500).json({ message: 'Failed to fetch matches' });
  }
});

module.exports = router;
