--[[
Created by Fxz_y

Usage: talk <sender> <message> [receiver]
Sends a public message, or a private one when a receiver is named.
]]
local tArgs = { ... }
local sender = tArgs[1] or "computer"
local message = tArgs[2]
local receiver = tArgs[3]

if not message then
  print("Usage: talk <sender> <message> [receiver]")
  return
end

local p = peripheral.find("chatBox")
if not p then
  print("No chat box attached.")
  return
end

local line = sender .. ": " .. message
if receiver then
  p.tell(receiver, line)
else
  p.say(line)
end
