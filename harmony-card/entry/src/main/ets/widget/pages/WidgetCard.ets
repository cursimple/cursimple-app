const storage = new LocalStorage();
@Entry(storage)
@Component
struct WidgetCard {
  @LocalStorageProp('heading') heading: string = '';
  @LocalStorageProp('title') title: string = '';
  @LocalStorageProp('detail') detail: string = '';
  @LocalStorageProp('count') count: string = '0';
  @LocalStorageProp('secondTitle') secondTitle: string = '';
  @LocalStorageProp('secondDetail') secondDetail: string = '';
  build() {
    Column({ space: 8 }) {
      Row() {
        Text(this.heading).fontSize(16).fontWeight(FontWeight.Bold).fontColor($r('app.color.primary')).maxLines(1).layoutWeight(1)
        Text(this.count).fontSize(16).fontColor($r('app.color.primary')).maxLines(1)
      }.width('100%')
      Text(this.title).fontSize(18).fontWeight(FontWeight.Bold).fontColor($r('app.color.text')).maxLines(2).width('100%')
      Text(this.detail).fontSize(12).fontColor($r('app.color.muted')).maxLines(2).width('100%')
      if (this.secondTitle) {
        Text(this.secondTitle).fontSize(14).fontWeight(FontWeight.Bold).fontColor($r('app.color.text')).maxLines(1).width('100%')
        Text(this.secondDetail).fontSize(11).fontColor($r('app.color.muted')).maxLines(1).width('100%')
      }
    }.width('100%').height('100%').padding(16).backgroundColor($r('app.color.surface')).borderRadius(20)
    .onClick(() => postCardAction(this, { action: 'router', abilityName: 'EntryAbility' }))
  }
}
