# Deployment Guide

## Overview

This guide covers all deployment methods for the WiFi GPS Hybrid Presence Detection System, including HPM installation, manual installation, and distribution preparation.

## Deployment Methods

### 1. HPM Deployment (Recommended)

#### Prerequisites
- Hubitat Package Manager (HPM) installed
- GitHub repository with proper structure
- Valid `packageManifest.json`

#### Steps
1. **Prepare HPM Package**:
   ```bash
   ./utilities/deployment-helper.sh hpm-validate
   ./utilities/deployment-helper.sh hpm-prepare
   ```

2. **Upload to GitHub**:
   - Create GitHub repository
   - Upload all files maintaining directory structure
   - Ensure raw file URLs are accessible

3. **Submit to HPM**:
   - Submit package manifest to HPM community
   - Wait for review and approval

#### HPM Package Structure
```
hpm-package/
├── packageManifest.json     # HPM manifest
├── apps/
│   └── presence-manager.groovy
├── drivers/
│   ├── wifi-gps-hybrid-presence.groovy
│   ├── anyone-presence-override.groovy
│   └── delayed-away-presence.groovy
├── docs/                    # Documentation
├── LICENSE
└── README.md
```

### 2. Manual Deployment

#### Prerequisites
- Hubitat hub with admin access
- Code validation completed
- Deployment package prepared

#### Steps
1. **Prepare Deployment**:
   ```bash
   ./utilities/deployment-helper.sh validate
   ./utilities/deployment-helper.sh prepare
   ```

2. **Install Drivers**:
   - Access Hubitat web interface
   - Go to Developer Tools > Drivers Code
   - For each driver in `deployment/drivers/`:
     - Click "New Driver"
     - Copy and paste driver code
     - Click "Save"

3. **Install App**:
   - Go to Developer Tools > Apps Code
   - Click "New App"
   - Copy and paste app code from `deployment/apps/`
   - Click "Save"

4. **Create App Instance**:
   - Go to Apps > Add User App
   - Select "WiFi GPS Presence Manager Enhanced"
   - Configure settings

### 3. Developer Deployment

#### Prerequisites
- Development environment set up
- Git repository access
- Testing environment

#### Steps
1. **Clone Repository**:
   ```bash
   git clone https://github.com/zekaizer/hubitat-presense.git
   cd hubitat-presense
   ```

2. **Development Setup**:
   ```bash
   chmod +x utilities/deployment-helper.sh
   ./utilities/deployment-helper.sh validate
   ```

3. **Local Testing**:
   - Deploy to test Hubitat hub
   - Test all functionality
   - Validate MQTT and GPS integration

4. **Prepare Release**:
   ```bash
   ./utilities/deployment-helper.sh release
   ```

## Deployment Scripts

### Available Commands

#### Code Validation
```bash
./utilities/deployment-helper.sh validate
```
- Validates Groovy syntax
- Checks for common errors
- Verifies file structure

#### Manual Deployment Preparation
```bash
./utilities/deployment-helper.sh prepare
```
- Creates deployment-ready files
- Generates deployment notes
- Prepares installation guide

#### HPM Package Validation
```bash
./utilities/deployment-helper.sh hpm-validate
```
- Validates `packageManifest.json`
- Checks required fields
- Verifies URL formats

#### HPM Package Preparation
```bash
./utilities/deployment-helper.sh hpm-prepare
```
- Creates HPM-ready package
- Includes all required files
- Generates package information

#### Release Creation
```bash
./utilities/deployment-helper.sh release
```
- Creates complete release package
- Includes both manual and HPM files
- Generates release notes

#### Backup Creation
```bash
./utilities/deployment-helper.sh backup
```
- Creates backup of current state
- Timestamps backup files
- Preserves development history

## File Structure Requirements

### Repository Structure
```
hubitat-presense/
├── apps/
│   └── presence-manager.groovy
├── drivers/
│   ├── wifi-gps-hybrid-presence.groovy
│   ├── anyone-presence-override.groovy
│   └── delayed-away-presence.groovy
├── docs/
│   ├── hpm-installation.md
│   ├── system-architecture.md
│   ├── data-flow.md
│   ├── api-reference.md
│   └── integration-guide.md
├── utilities/
│   ├── deployment-helper.sh
│   ├── development-guide.md
│   └── test-suite.groovy
├── packageManifest.json
├── LICENSE
├── README.md
└── .gitignore
```

### GitHub Repository Requirements
- Public repository for HPM access
- Proper branch structure (main/master)
- Tagged releases for versioning
- Raw file access enabled

## Version Management

### Semantic Versioning
- Format: `MAJOR.MINOR.PATCH`
- Major: Breaking changes
- Minor: New features, backwards compatible
- Patch: Bug fixes

### Version Update Process
1. Update version in `packageManifest.json`
2. Update changelog in README.md
3. Create git tag
4. Run release script
5. Upload to GitHub
6. Update HPM if needed

### Example Version Update
```bash
# Update version in packageManifest.json
jq '.version = "1.1.0"' packageManifest.json > temp.json && mv temp.json packageManifest.json

# Create release
./utilities/deployment-helper.sh release

# Create git tag
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin v1.1.0
```

## Quality Assurance

### Pre-Deployment Checklist
- [ ] Code validation passes
- [ ] All drivers compile without errors
- [ ] App installs and runs
- [ ] HPM manifest validates
- [ ] Documentation updated
- [ ] Version numbers updated
- [ ] Tests pass (if available)

### Testing Requirements
- Test on clean Hubitat hub
- Verify MQTT integration
- Test GPS webhook functionality
- Check manual override features
- Validate delayed away logic
- Test with multiple devices

### Code Quality Standards
- Follow Hubitat coding conventions
- Include proper error handling
- Add comprehensive logging
- Document all public methods
- Use consistent naming conventions

## Distribution Channels

### HPM Distribution
- **Audience**: General users
- **Installation**: One-click via HPM
- **Updates**: Automatic via HPM
- **Support**: Community forums

### Manual Distribution
- **Audience**: Advanced users, developers
- **Installation**: Manual code deployment
- **Updates**: Manual code updates
- **Support**: GitHub issues

### Development Distribution
- **Audience**: Developers, testers
- **Installation**: Git clone and deploy
- **Updates**: Git pull and redeploy
- **Support**: Direct development feedback

## Troubleshooting Deployment

### Common Issues

#### HPM Package Not Found
```
Problem: HPM can't find the package
Solutions:
- Check GitHub repository is public
- Verify packageManifest.json is valid
- Ensure raw file URLs are accessible
- Wait for HPM index update
```

#### Manual Installation Errors
```
Problem: Code doesn't compile in Hubitat
Solutions:
- Check Groovy syntax with validator
- Verify all required imports
- Check for platform-specific issues
- Review Hubitat logs for errors
```

#### Version Conflicts
```
Problem: Multiple versions installed
Solutions:
- Remove old versions before installing
- Check for namespace conflicts
- Clear device cache if needed
- Restart Hubitat hub
```

### Debug Process
1. **Check Logs**: Review Hubitat logs for errors
2. **Validate Code**: Run deployment helper validation
3. **Test Isolation**: Test individual components
4. **Community Help**: Post in Hubitat forums
5. **Issue Reports**: Create GitHub issues with details

## Security Considerations

### Code Security
- No hardcoded credentials
- Secure MQTT authentication
- Input validation for webhooks
- Proper error handling

### Distribution Security
- Signed releases where possible
- Checksum verification
- Secure download channels
- Regular security updates

### Deployment Security
- Use HTTPS for all downloads
- Verify source authenticity
- Review code before installation
- Regular security audits

## Maintenance and Updates

### Regular Maintenance
- Monthly security reviews
- Quarterly dependency updates
- Annual architecture reviews
- Continuous community feedback

### Update Process
1. **Plan Update**: Define scope and changes
2. **Develop**: Implement changes with tests
3. **Test**: Validate on test environment
4. **Package**: Create release package
5. **Deploy**: Update HPM and manual distributions
6. **Monitor**: Watch for issues post-deployment

### Support Channels
- **GitHub Issues**: Bug reports and feature requests
- **Community Forums**: General support and discussions
- **Documentation**: Comprehensive guides and API docs
- **Email Support**: Direct developer contact (if available)

## Conclusion

The deployment system provides multiple pathways for users to install and maintain the WiFi GPS Hybrid Presence Detection System. HPM provides the simplest experience for most users, while manual deployment offers maximum control for advanced users and developers.

Regular maintenance and updates ensure the system remains secure, stable, and feature-rich for the Hubitat community.