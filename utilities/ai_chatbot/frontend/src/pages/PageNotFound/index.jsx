import React from 'react';

/* React Router */
import { Link, withRouter } from 'react-router-dom';

/* Assets */
import Page404Img from '../../assets/svg/cone.min.svg';

function PageNotFound() {
  return (
    <main className='flex flex-col items-center justify-center'>
      <img src={Page404Img} alt='Error' className='w-24 my-10' />
      <h2 className='my-4 text-5xl font-semibold'>Page Not Found</h2>
      <h5 className='mb-4 px-6 text-center text-xl font-medium text-gray-400'>Sorry, we couldn't find the page you're looking for.</h5>
      <Link to={'/'} className='font-semibold underline text-blue-800'>
        Back To Homepage
      </Link>
    </main>
  );
}

export default withRouter(PageNotFound);
