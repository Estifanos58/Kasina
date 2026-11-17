import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { createChatSlice } from '../src/store/slices/chat-slice.js';

describe('Chat Slice Unit Tests', () => {
  function createTestStore(initialState = {}) {
    let state = {};

    const get = () => state;
    const set = (partial) => {
      state = typeof partial === 'function' ? { ...state, ...partial(state) } : { ...state, ...partial };
    };

    const slice = createChatSlice(set, get);
    state = { ...slice, ...initialState };
    return { get, set, slice: state };
  }

  it('should initialize with correct default state', () => {
    const { get } = createTestStore();
    const state = get();

    assert.equal(state.selectedChatType, undefined);
    assert.equal(state.selectedChatData, undefined);
    assert.deepEqual(state.selectedChatMessages, []);
    assert.deepEqual(state.directMessagesContacts, []);
    assert.deepEqual(state.channels, []);
  });

  it('should add message and prevent duplicates by ID', () => {
    const { get, slice } = createTestStore();

    slice.addMessage({ id: 'msg-1', text: 'Hello', timestamps: '2026-01-01T10:00:00Z' });
    assert.equal(get().selectedChatMessages.length, 1);

    // Duplicate message with same id
    slice.addMessage({ id: 'msg-1', text: 'Hello again', timestamps: '2026-01-01T10:00:00Z' });
    assert.equal(get().selectedChatMessages.length, 1);

    // Different message
    slice.addMessage({ id: 'msg-2', text: 'Second message', timestamps: '2026-01-01T10:05:00Z' });
    assert.equal(get().selectedChatMessages.length, 2);
  });

  it('should sort messages chronologically by timestamp', () => {
    const { get, slice } = createTestStore();

    // Add in reverse chronological order
    slice.addMessage({ id: 'msg-3', text: 'Latest', timestamps: '2026-01-01T12:00:00Z' });
    slice.addMessage({ id: 'msg-1', text: 'Earliest', timestamps: '2026-01-01T10:00:00Z' });
    slice.addMessage({ id: 'msg-2', text: 'Middle', timestamp: '2026-01-01T11:00:00Z' });

    const messages = get().selectedChatMessages;
    assert.equal(messages.length, 3);
    assert.equal(messages[0].id, 'msg-1');
    assert.equal(messages[1].id, 'msg-2');
    assert.equal(messages[2].id, 'msg-3');
  });

  it('should move active channel to top of channel list on new channel message', () => {
    const { get, slice } = createTestStore({
      channels: [
        { id: 'chan-1', name: 'General' },
        { id: 'chan-2', name: 'Random' },
        { id: 'chan-3', name: 'Dev' },
      ],
    });

    slice.addChannelInChannelList({ channelId: 'chan-3' });

    const channels = get().channels;
    assert.equal(channels[0].id, 'chan-3');
    assert.equal(channels[1].id, 'chan-1');
    assert.equal(channels[2].id, 'chan-2');
  });

  it('should close chat and reset active chat state', () => {
    const { get, slice } = createTestStore({
      selectedChatType: 'contact',
      selectedChatData: { id: 'user-1' },
      selectedChatMessages: [{ id: 'm1' }],
    });

    slice.closedChat();

    const state = get();
    assert.equal(state.selectedChatType, undefined);
    assert.equal(state.selectedChatData, undefined);
    assert.deepEqual(state.selectedChatMessages, []);
  });

  it('should add new channel at the beginning of the list', () => {
    const { get, slice } = createTestStore({
      channels: [{ id: 'c1', name: 'General' }],
    });

    slice.addChannel({ id: 'c2', name: 'Announcements' });

    const channels = get().channels;
    assert.equal(channels.length, 2);
    assert.equal(channels[0].id, 'c2');
    assert.equal(channels[1].id, 'c1');
  });
});
