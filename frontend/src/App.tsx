import { Route, Routes } from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import CreateRoomPage from './pages/CreateRoomPage'
import JoinRoomPage from './pages/JoinRoomPage'
import RoomCreatedPage from './pages/RoomCreatedPage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/create" element={<CreateRoomPage />} />
      <Route path="/join" element={<JoinRoomPage />} />
      <Route path="/room/:code" element={<RoomCreatedPage />} />
    </Routes>
  )
}

export default App
