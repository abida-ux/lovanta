const mongoose = require('mongoose');

const userSchema = new mongoose.Schema(
  {
    name: {
      type: String,
      required: true,
      trim: true,
    },
    email: {
      type: String,
      required: true,
      unique: true,
      lowercase: true,
      trim: true,
    },
    password: {
      type: String,
      required: true,
      minlength: 6,
    },
    profileComplete: {
      type: Boolean,
      default: false,
    },
    bio: {
      type: String,
      default: '',
    },
    age: {
      type: Number,
      default: null,
    },
    location: {
      type: String,
      default: '',
    },
    interests: {
      type: [String],
      default: [],
    },
    avatar: {
      type: String,
      default: '',
    },
    gallery: {
      type: [String],
      default: [],
    },
    likes: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
    dislikes: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
    matches: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  },
  {
    timestamps: true,
  }
);

module.exports = mongoose.model('User', userSchema);
