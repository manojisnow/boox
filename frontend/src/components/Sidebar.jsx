import React from 'react';
import './Sidebar.css';

export function SidebarToggle({ expanded, setExpanded }) {
    return !expanded ? (
        <button className="sidebar-toggle sidebar-toggle-collapsed" onClick={() => setExpanded(true)} aria-label="Expand sidebar">
            <span className="sidebar-hamburger">&#9776;</span>
        </button>
    ) : null;
}

const Sidebar = ({ expanded, setExpanded }) => {
    return (
        <div className={`sidebar${expanded ? '' : ' collapsed'}`}>
            {expanded && (
                <button className="sidebar-toggle sidebar-toggle-expanded" onClick={() => setExpanded(false)} aria-label="Collapse sidebar">
                    <span className="sidebar-hamburger">&#9776;</span>
                </button>
            )}
            <div className="sidebar-header">Saved Chats</div>
            <div className="sidebar-content">
                <div className="sidebar-placeholder">(No saved chats yet)</div>
            </div>
        </div>
    );
};

export default Sidebar; 