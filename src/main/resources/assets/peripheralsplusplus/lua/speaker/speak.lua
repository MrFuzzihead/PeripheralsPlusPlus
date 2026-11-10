--[[
Created by austinv11

Usage: speak <text> [text...]
Speaks each argument, waiting for the speaker to report completion.
]]
local LANGUAGE = "English" -- change this to change the voice

local p = peripheral.find("speaker")
if not p then
  print("No speaker attached.")
  return
end

local words = { ... }
if #words == 0 then
  print("Usage: speak <text> [text...]")
  return
end

for _, value in pairs(words) do
  if p.speak(value, 64, LANGUAGE) then
    -- Only wait when the peripheral accepted the request; otherwise the event never comes.
    os.pullEvent("speechComplete")
  end
end
