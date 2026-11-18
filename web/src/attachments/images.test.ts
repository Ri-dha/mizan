import { toGrey } from "./images"

describe("toGrey", () => {
  it("replaces each pixel with its luma and keeps transparency", () => {
    const pixels = new Uint8ClampedArray([255, 0, 0, 255, 0, 255, 0, 128, 255, 255, 255, 255])
    toGrey(pixels)
    expect([...pixels]).toEqual([76, 76, 76, 255, 150, 150, 150, 128, 255, 255, 255, 255])
  })
})
