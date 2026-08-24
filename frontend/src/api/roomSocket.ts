import { Client, type IMessage } from '@stomp/stompjs'
import { API_BASE_URL, getPublicRoomState, type RoomState } from './rooms'

const WS_BASE_URL = API_BASE_URL.replace(/^http/, 'ws')

/**
 * Subscribes to real-time updates for a room (players joining, game
 * starting). Returns a cleanup function that closes the connection.
 *
 * Also fetches current state once on every connect (including
 * reconnects) rather than relying solely on the next push — the
 * in-memory broker doesn't replay missed messages, so a client whose
 * socket wasn't connected yet, or had silently dropped (e.g. a phone
 * locking mid-wait), would otherwise never learn the room moved on.
 */
export function subscribeToRoom(code: string, onUpdate: (state: RoomState) => void): () => void {
  const client = new Client({
    brokerURL: `${WS_BASE_URL}/ws`,
    reconnectDelay: 3000,
    onConnect: () => {
      getPublicRoomState(code).then(onUpdate).catch(() => {})
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
 *
 * Also fires once on every connect (including reconnects), for the
 * same reason as {@link subscribeToRoom}: a dropped connection means a
 * missed push is gone for good, so the master's own client (e.g. after
 * their phone locks mid-game) needs to re-check on reconnect rather
 * than wait for the next change.
 */
export function subscribeToGame(code: string, onUpdate: () => void): () => void {
  const client = new Client({
    brokerURL: `${WS_BASE_URL}/ws`,
    reconnectDelay: 3000,
    onConnect: () => {
      onUpdate()
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
