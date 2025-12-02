import React, { useEffect, useState } from 'react';
import { Typography } from 'antd';
import { formatText } from '../utils';

const { Text } = Typography;

const TypingEffect = ({ text, onTypingComplete, scrollContainerRef }) => {
  const [displayedText, setDisplayedText] = useState('');
  const [currentIndex, setCurrentIndex] = useState(0);

  // dynamic typing speed
  const typingSpeed = text.length > 1500 ? 1 : 5; // ms

  useEffect(() => {
    if (currentIndex < text.length) {
      const timer = setTimeout(() => {
        setDisplayedText((prev) => prev + text[currentIndex]);
        setCurrentIndex((prev) => prev + 1);
      }, typingSpeed);

      return () => clearTimeout(timer);
    } else if (onTypingComplete) {
      onTypingComplete();
    }
  }, [currentIndex, text, typingSpeed]);

  // Scroll when displayed text changes
  useEffect(() => {
    if (scrollContainerRef?.current) {
      // Add a small delay to ensure the DOM updates
      setTimeout(() => {
        scrollContainerRef.current.scrollTo({ top: scrollContainerRef.current.scrollHeight, behavior: 'smooth' });
      }, 50); // Adjust the delay if needed
    }
  }, [displayedText, scrollContainerRef]);

  return <Text>{formatText(displayedText)}</Text>;
};

export default TypingEffect;
