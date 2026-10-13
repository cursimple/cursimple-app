import { common } from '@kit.AbilityKit';
import { picker } from '@kit.CoreFileKit';
import { CardStore } from '../common/CardStore';

@Entry
@Component
struct Index {
  @State status: string = '';
  @State busy: boolean = false;
  private async importFile(notes: boolean): Promise<void> {
    if (this.busy) return;
    this.busy = true;
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const selector = new picker.DocumentViewPicker(context);
      const options = new picker.DocumentSelectOptions();
      options.maxSelectNumber = 1; options.fileSuffixFilters = [notes ? '.csnotes' : '.ics'];
      const files = await selector.select(options);
      if (files.length > 0) {
        const count = await new CardStore(context).importFile(files[0], notes);
        this.status = context.resourceManager.getStringByNameSync('import_success') + ' ' + count;
      }
    } catch (_) {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      this.status = context.resourceManager.getStringByNameSync('import_failed');
    } finally { this.busy = false; }
  }
  build() {
    Column({ space: 20 }) {
      Text($r('app.string.app_name')).fontSize(28).fontWeight(FontWeight.Bold).fontColor($r('app.color.text')).maxLines(1)
      Text($r('app.string.home_description')).fontSize(16).fontColor($r('app.color.muted'))
      Button($r('app.string.import_notes')).height(52).width('100%').enabled(!this.busy).backgroundColor($r('app.color.primary')).onClick(() => this.importFile(true))
      Button($r('app.string.import_calendar')).height(52).width('100%').enabled(!this.busy).backgroundColor($r('app.color.primary')).onClick(() => this.importFile(false))
      Text($r('app.string.add_hint')).fontSize(16).fontColor($r('app.color.muted'))
      if (this.status) Text(this.status).fontSize(16).fontColor($r('app.color.text'))
    }.width('100%').height('100%').padding(24).backgroundColor($r('app.color.background'))
  }
}
