import React from 'react';

interface CentiumLogoProps {
  className?: string;
  size?: number | string;
  color?: string;
  animated?: boolean;
}

export const CentiumLogo: React.FC<CentiumLogoProps> = ({
  className = '',
  size = 32,
  color = '#6C4DFF',
  animated = false,
}) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 512 512"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 select-none ${animated ? 'transition-all duration-300' : ''} ${className}`}
      aria-label="Centium Logo"
    >
      {/* Outer Hexagonal Shield "C" */}
      <path
        d="M 374 162 L 256 94 L 138 162 L 138 350 L 256 418 L 374 350"
        stroke={color}
        strokeWidth="50"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Center Onion: Central Petal with Sprout Tip */}
      <path
        d="M 256 168 
           C 257.5 174, 261 184, 261.5 188 
           C 262 192, 259 196, 258 200 
           C 272 230, 274 275, 256 338 
           C 238 275, 240 230, 254 200 
           C 253 196, 250 192, 250.5 188 
           C 251 184, 254.5 174, 256 168 Z"
        fill={color}
      />

      {/* Center Onion: Left Petal */}
      <path
        d="M 244 204 
           C 216 226, 194 262, 202 300 
           C 208 326, 226 338, 240 334 
           C 222 305, 220 255, 244 204 Z"
        fill={color}
      />

      {/* Center Onion: Right Petal */}
      <path
        d="M 268 204 
           C 296 226, 318 262, 310 300 
           C 304 326, 286 338, 272 334 
           C 290 305, 292 255, 268 204 Z"
        fill={color}
      />
    </svg>
  );
};
