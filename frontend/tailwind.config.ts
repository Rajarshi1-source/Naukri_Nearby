import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: "#0d7a5f",
          dark: "#0a5f4a",
          light: "#e6f4ef",
        },
      },
    },
  },
  plugins: [],
};

export default config;
