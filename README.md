# MelonMC Minecraft Server Website

A premium website for the MelonMC Minecraft server, featuring an eye-catching design with animations and a built-in admin panel.

## Features

- Responsive design for all devices
- Eye-catching animations and visuals
- Server information and features showcase
- Ranks and pricing information
- Admin panel for website management
- Newsletter sign-up

## Deployment on Netlify

This website is optimized for deployment on Netlify's free hosting.

### One-Click Deployment

You can deploy this site to Netlify with a single click:

[![Deploy to Netlify](https://www.netlify.com/img/deploy/button.svg)](https://app.netlify.com/start/deploy?repository=https://github.com/yourusername/melonmc-website)

### Manual Deployment Steps

1. **Fork or clone this repository**

2. **Create a new site on Netlify**
   - Go to [Netlify](https://app.netlify.com/)
   - Click "New site from Git"
   - Choose your Git provider and select your repository
   - Use the following build settings:
     - Build command: (leave blank)
     - Publish directory: `Website/`

3. **Configure the site**
   - In Site settings > Build & deploy > Continuous Deployment, make sure the "Publish directory" is set to `Website/`
   - Enable form detection for the newsletter form

### Environment Variables

No environment variables are required for basic functionality.

### Custom Domain

To set up a custom domain:
1. Go to Site settings > Domain management
2. Click "Add custom domain"
3. Follow the instructions to set up DNS records

## Admin Panel

The website includes a simple admin panel for website management.

- **URL**: `/admin/`
- **Default credentials**: 
  - Username: admin
  - Password: admin123

In the free Netlify tier, the admin panel uses client-side storage (Firebase and localStorage) to store data. This means:
- Data is stored in the user's browser
- Changes are visible only to the current user
- For a production site, you'd want to upgrade to a paid service with server-side storage

### Upgrading the Admin Panel

To upgrade to a full backend service:
1. Create a Firebase project
2. Update the Firebase configuration in `admin.js`
3. Enable Firebase Authentication and Firestore
4. Set up proper security rules

## Local Development

1. Clone the repository
2. Open the `Website/` directory in your favorite IDE
3. Use a local server to preview the site (e.g., Live Server in VSCode)

## Customization

- Edit `index.html` for content changes
- Modify `styles.css` for design changes
- Update `script.js` for functionality changes

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

Created with ❤️ for MelonMC Server 