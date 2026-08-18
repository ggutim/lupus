import { Route, Routes } from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import CreateRoomPage from './pages/CreateRoomPage'
import JoinRoomPage from './pages/JoinRoomPage'
import RoomCreatedPage from './pages/RoomCreatedPage'
import RoomRosterPage from './pages/RoomRosterPage'
import PlayerWaitingPage from './pages/PlayerWaitingPage'
import PlayerRolePage from './pages/PlayerRolePage'
import MasterGamePage from './pages/MasterGamePage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/create" element={<CreateRoomPage />} />
      <Route path="/join" element={<JoinRoomPage />} />
      <Route path="/room/:code" element={<RoomCreatedPage />} />
      <Route path="/room/:code/roster" element={<RoomRosterPage />} />
      <Route path="/room/:code/waiting" element={<PlayerWaitingPage />} />
      <Route path="/room/:code/role" element={<PlayerRolePage />} />
      <Route path="/room/:code/game" element={<MasterGamePage />} />
    </Routes>
  )
}

export default App
