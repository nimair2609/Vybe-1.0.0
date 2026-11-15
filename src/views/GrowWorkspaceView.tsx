import React, { useState, useEffect } from 'react';
import { useVybe } from '../context/VybeContext';
import { ProfileView } from './ProfileView';
import { GrowHomeTab } from './grow/GrowHomeTab';
import { GrowProgramsTab } from './grow/GrowProgramsTab';
import { GrowNetworkTab } from './grow/GrowNetworkTab';
import { GrowProjectsTab } from './grow/GrowProjectsTab';
import { GrowOpportunitiesTab } from './grow/GrowOpportunitiesTab';

export const GrowWorkspaceView: React.FC = () => {
  const { growTab } = useVybe();
  const [showProgramsView, setShowProgramsView] = useState(false);

  // Reset programs sub-view whenever top-level growTab switches
  useEffect(() => {
    setShowProgramsView(false);
  }, [growTab]);

  if (growTab === 'PROFILE') {
    return <ProfileView />;
  }

  if (showProgramsView) {
    return <GrowProgramsTab onBack={() => setShowProgramsView(false)} />;
  }

  switch (growTab) {
    case 'CIRCLE':
      return <GrowNetworkTab />;
    case 'PROJECTS':
      return <GrowProjectsTab onOpenPrograms={() => setShowProgramsView(true)} />;
    case 'OPPORTUNITIES':
      return <GrowOpportunitiesTab />;
    case 'HOME':
    default:
      return <GrowHomeTab onOpenPrograms={() => setShowProgramsView(true)} />;
  }
};
