package checkers;

import java.awt.Color;

public final class Theme {
  private Theme() {}

  // --- Warm Wood ---
  public static final Color WINDOW_BG     = new Color(0xEE,0xEA,0xE4); // #EEEAE4
  public static final Color BOARD_LIGHT   = new Color(0xF4,0xE2,0xC1); // #F4E2C1
  public static final Color BOARD_DARK    = new Color(0xD8,0xB3,0x84); // #D8B384
  public static final Color GRID_LINE     = new Color(0x8B,0x6A,0x3E); // #8B6A3E
  public static final Color PIECE_BLACK   = new Color(0x1E,0x1E,0x1E); // #1E1E1E
  // public static final Color PIECE_WHITE   = new Color(0xEC,0xE7,0xDB); // #ECE7DB
  // public static final Color PIECE_WHITE   = new Color(0xEC, 0x14, 0x14, 0xFF); // #ec1414ff
  public static final Color PIECE_WHITE   = new Color(0x7A, 0x11, 0x11, 0xFF);
  public static final Color PARCHMENT_BG  = new Color(0xF6,0xE9,0xD7); // #F6E9D7
  public static final Color KING_CENTER   = new Color(0xD4,0xAF,0x37); // #D4AF37

  public static final Color MOVE_HL       = new Color( 37,  99, 235,  51); // rgba(...,0.20)
  public static final Color JUMP_HL       = new Color(245, 158,  11,  64); // rgba(...,0.25)

  public static final Color BORDER   = new Color(0xD4,0xAF,0x37); // #D4AF37

  // public static final Color MENU_BG     = new Color(0xF1,0xE5,0xCF);  // #F1E5CF
  public static final Color MENU_BG = new Color(0xD8,0xB3,0x84); // #D8B384
  public static final Color MENU_CARD   = PARCHMENT_BG;               // #F6E9D7
  public static final Color MENU_BORDER = GRID_LINE;                  // #8B6A3E
}
