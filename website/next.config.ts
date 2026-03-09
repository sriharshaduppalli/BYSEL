import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Static export for Apache hosting.
  output: "export",
  // Apache commonly normalizes to trailing slashes for directories.
  trailingSlash: true,
  images: {
    // Required when serving static export without Next image optimizer.
    unoptimized: true,
  },
};

export default nextConfig;
