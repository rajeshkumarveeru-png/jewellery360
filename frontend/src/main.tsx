import React from 'react';import{createRoot}from'react-dom/client';import'./styles.css';import App from'./App';import'./premium.css';import'./jewel.css';import'./pro.css';import'./packs.css';import'./drawer.css'; // premium.css must load AFTER every page stylesheet so its rules win
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
