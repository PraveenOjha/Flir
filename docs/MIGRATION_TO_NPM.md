# Flir: Migration to npm package and CocoaPods best practices

This document explains the recommended workflow for consuming the `Flir` (ilabs-flir) SDK wrapper either via npm autolinked package or as a local in-repo podspec. Following the recommended approach avoids CocoaPods double-declaration and installation failures.

## Why this matters
- Having the same pod declared from two different sources (e.g., a `node_modules` package that autolinks `Flir`, plus a local podspec `ios/podspecs/Flir.podspec`) causes CocoaPods to detect duplicate pod names and fail to install pods.
- The recommended pattern is to use a single authoritative source for the `Flir` pod: either the npm package or the local repository path.

## Recommended setups

### 1) Production / CI / Dev apps that consume `ilabs-flir` from npm
- Install the npm package into your project:
```bash
npm install ilabs-flir
```
- Run `pod install` in your Xcode workspace. Autolinking will add the `Flir` pod for you.
- Remove or do not commit any in-repo `Flir.podspec` files if you do not need them locally.
- Our `Podfile` contains additional detection logic to skip declaring `Flir` if it is already autolinked.

### 2) Local development of the Flir native module (you maintain `Flir` in a sibling repo)
- If you prefer working on the native Flir library locally, prefer the local repo over npm autolinking. For example, keep the sibling repo checked out as `../Flir`.
- If you do this, either:
  - Remove the `ilabs-flir` npm package from `node_modules` while developing locally, or
  - Make sure your Podfile picks the local project (the Podfile contains logic to prefer the local path when `node_modules` package is not present).

### 3) Avoiding duplicates if you still want an in-repo podspec
- If you must keep an in-repo `Flir.podspec` (for custom packaging or local builds), ensure you do not also have `ilabs-flir` installed via npm in the consuming app.

## Safe default and CI-friendly approach
- Prefer the npm package for CI and long-term stability. The package will contain the necessary native code for most use cases and is autolinked by React Native.
- Exclude podspec files from published packages, so the npm package does not publish a podspec that could trigger a duplicate pod declaration in apps that still have in-repo copies.

## Short checklist for app devs
- Prefer `npm install ilabs-flir` and remove any `ios/podspecs/Flir.podspec` from the app repo.
- If you work on native Flir locally, remove `ilabs-flir` from `node_modules` and use the local repo path, or use the `config` in Podfile to prefer local path.
- If you use both local and npm (not recommended), be careful that only one pod declaration exists in the CocoaPods graph.

## Podfile protection and how it works in this project
- The app `Podfile` now includes detection logic that checks if autolinking already declared `Flir` (including nested occurrences like plugins or configuration objects). If detected, the Podfile skips adding `pod 'Flir'` explicitly.
- This protects apps that accidentally have the in-repo `Flir.podspec` alongside the npm package.

## Contact and support
- If you run into `CocoaPods: Multiple sources for pod Flir` or a similar duplicate name error, confirm what `Flir` sources are declared (`node_modules`, `../Flir` local repo, or in-repo `ios/podspecs`), remove one of them, or update your Podfile to point to the desired one.
- If you want me to enforce a single source (e.g., always prefer `node_modules`), I can update the repo and README accordingly.

---

Made by the ilabs-flir team.