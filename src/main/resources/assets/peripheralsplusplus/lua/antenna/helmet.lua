--[[
Created by Fxz_y

Edit the settings below, then run this program to push a HUD to a smart helmet.
Set any unused option to nil.
]]
local PLAYER = nil -- the player wearing the helmet
local PRINT_RESOLUTION = false -- print the helmet's resolution
local EASY_MESSAGE = nil -- a plain string, shown top-left
local MESSAGE = nil -- a string drawn at a position with a colour
local STRING_X, STRING_Y = nil, nil
local STRING_R, STRING_G, STRING_B = nil, nil, nil
local TEXTURE = nil -- unlocalised texture name
local TEXTURE_X, TEXTURE_Y, TEXTURE_W, TEXTURE_H, TEXTURE_U, TEXTURE_V = nil, nil, nil, nil, nil, nil
local RECT = false -- draw a rectangle
local RECT_X1, RECT_Y1, RECT_X2, RECT_Y2 = nil, nil, nil, nil
local RECT_R, RECT_G, RECT_B = nil, nil, nil
local RECT_TO_R, RECT_TO_G, RECT_TO_B = nil, nil, nil -- optional gradient
local HOR_LINE = false -- draw a horizontal line
local HOR_X1, HOR_X2, HOR_Y = nil, nil, nil
local HOR_R, HOR_G, HOR_B = nil, nil, nil
local VER_LINE = false -- draw a vertical line
local VER_X, VER_Y1, VER_Y2 = nil, nil, nil
local VER_R, VER_G, VER_B = nil, nil, nil

local p = peripheral.find("antenna")
if not p then
  print("No antenna attached.")
  return
end
if not PLAYER then
  print("Set PLAYER before running this program.")
  return
end

local obj = p.getHUD(PLAYER)
if not obj then
  print("The antenna could not find " .. tostring(PLAYER) .. ".")
  return
end

if PRINT_RESOLUTION then
  local width, height = obj.getResolution()
  print(tostring(PLAYER) .. "'s resolution is " .. tostring(width) .. "x" .. tostring(height))
end

if EASY_MESSAGE then
  obj.sendMessage(EASY_MESSAGE)
end

if MESSAGE then
  obj.drawString(MESSAGE, STRING_X, STRING_Y, obj.getColorFromRGB(STRING_R, STRING_G, STRING_B))
end

if TEXTURE then
  obj.drawTexture(TEXTURE, TEXTURE_X, TEXTURE_Y, TEXTURE_W, TEXTURE_H, TEXTURE_U, TEXTURE_V)
end

if RECT then
  local colour = obj.getColorFromRGB(RECT_R, RECT_G, RECT_B)
  if RECT_TO_R and RECT_TO_G and RECT_TO_B then
    obj.drawRectangle(
      RECT_X1, RECT_Y1, RECT_X2, RECT_Y2, colour, obj.getColorFromRGB(RECT_TO_R, RECT_TO_G, RECT_TO_B))
  else
    obj.drawRectangle(RECT_X1, RECT_Y1, RECT_X2, RECT_Y2, colour)
  end
end

if HOR_LINE then
  obj.drawHorizontalLine(HOR_X1, HOR_Y, HOR_X2, obj.getColorFromRGB(HOR_R, HOR_G, HOR_B))
end

if VER_LINE then
  obj.drawVerticalLine(VER_X, VER_Y1, VER_Y2, obj.getColorFromRGB(VER_R, VER_G, VER_B))
end

obj.sync()
