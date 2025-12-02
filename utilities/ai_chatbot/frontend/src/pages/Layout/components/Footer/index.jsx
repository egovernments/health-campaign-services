import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PrescienceLogo from '../../../../assets/imgs/prescience.png';

const Footer = () => {
  const [version, setVersion] = useState(''); // State to store the version

  useEffect(() => {
    const savedVersion = localStorage.getItem('appVersion');
    if (savedVersion) {
      setVersion(savedVersion);
    }
  }, []);

  return (
    <main className='flex flex-nowrap justify-center sticky bottom-0' style={{ backgroundColor: '#fafafa' }}>
      <span className='flex flex-row items-center md:my-1'>
        Powered By &nbsp;
        <Link to={{ pathname: `https://prescienceds.com/` }} className='' target='_blank'>
          <img src={PrescienceLogo} alt='Prescience Decision Solutions' className='w-auto h-8' />
        </Link>
        {version && (
          <>
            <span className='ml-2'>|</span>
            <span className='ml-2 mt-0.5'>Version: {version}</span>
          </>
        )}
      </span>
    </main>
  );
};

export default Footer;
