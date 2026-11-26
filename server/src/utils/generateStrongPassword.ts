import { randomInt } from "node:crypto";

const CHARSETS = [
  "ABCDEFGHJKLMNPQRSTUVWXYZ",
  "abcdefghijkmnopqrstuvwxyz",
  "23456789",
  "!@#$%^&*()-_=+[]{}",
];
const ALL_CHARACTERS = CHARSETS.join("");

export function generateStrongPassword(): string {
  const characters = CHARSETS.map((charset) => charset[randomInt(charset.length)]);
  while (characters.length < 8) {
    characters.push(ALL_CHARACTERS[randomInt(ALL_CHARACTERS.length)]);
  }
  for (let index = characters.length - 1; index > 0; index--) {
    const swapIndex = randomInt(index + 1);
    [characters[index], characters[swapIndex]] = [characters[swapIndex], characters[index]];
  }
  return characters.join("");
}
