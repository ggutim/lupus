import { Client, type IMessage } from '@stomp/stompjs'
import { API_BASE_URL, type RoomState } from './rooms'

const WS_BASE_URL = API_BASE_URL.replace(/^http/, 'ws')

/**
 * Subscribes to real-time updates for a room (players joining, game
 * starting). Returns a cleanup function that closes the connection.
 */
export function subscribeToRoom(code: string, onUpdate: (state: RoomState) => void): () => void {
  const client = new Client({
    brokerURL: `${WS_BASE_URL}/ws`,
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(`/topic/rooms/${code}`, (message: IMessage) => {
        onUpdate(JSON.parse(message.body) as RoomState)
      })
    },
  })

  client.activate()

  return () => {
    client.deactivate()
  }
}

/**
 * Subscribes to real-time "something changed" pings for an in-progress
 * game. The broadcast itself carries no secret information (no roles,
 * no pending selections) — it only signals the master's client to go
 * re-fetch the full authenticated game state via {@link getGameState}.
 */
export function subscribeToGame(code: string, onUpdate: () => void): () => void {
  const client = new Client({
    brokerURL: `${WS_BASE_URL}/ws`,
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(`/topic/rooms/${code}/game`, (_message: IMessage) => {
        onUpdate()
      })
    },
  })

  client.activate()

  return () => {
    client.deactivate()
  }
}
